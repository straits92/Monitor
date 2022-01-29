package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.parseutils.ParseUtils;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/* Background execution module. Takes care of:
* fetching location information from android GPS module,
* fetching "location code" and weather data from Accuweather API
* formatting and storing data in location and weather database
* maintaining data stored in the weather database
* */
public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel";

    /* enumerated constants */
    private static final Integer TEMPERATURE_SENSOR_INSTANT = 3;
    private static final Integer TEMPERATURE_SENSOR = 2;
    private static final Integer SINGLE_HOUR_DATA = 1;
    private static final Integer TWELVE_HOURS_DATA = 0;

    private static final Integer UNDER_48H = 0;
    private static final Integer BETWEEN_48H_AND_WEEK = 1;
    private static final Integer MORE_THAN_A_WEEK = 2;

    /* package instant sensor reading into LiveData, separate from any db updates */
    private static MutableLiveData<String> instantSensorReading;

    private static RemoteDataFetchModel instance;
    private static WeatherDao weatherDaoReference;
    private static LocationDao locationDaoReference;
    private static Application applicationFromRepository;
    private static MonitorLocation defaultHomeLocation;
    private static List<MonitorLocation> defaultMonitorLocationList;

    private static ExecutorService networkExecutor;
    private static ExecutorService cachingExecutor;
    private static ExecutorService serviceExecutor;
    private static ExecutorService gpsExecutor;
    private static ScheduledExecutorService scheduledExecutor;

    /* singleton, instantiated in environment providing DAO and reference to activity  */
    public static RemoteDataFetchModel getInstance(WeatherDao weatherDao, LocationDao locationDao,
                                                   Application application,
                                                   MutableLiveData<String> instantSensorReadingObj) {
        if (instance == null) {
            /* set up private members */
            instance = new RemoteDataFetchModel();
            weatherDaoReference = weatherDao;
            locationDaoReference = locationDao;
            applicationFromRepository = application;
            instantSensorReading = instantSensorReadingObj;

            /* define scheduling parameters in seconds */
            Integer howLongVisible = 172800; /* 60 * 60 * 24 * 2 , 48H */
            Integer howLongStored = 604800; /* 60 * 60 * 24 * 7, should be a week */

            /* instantiate all necessary executors; never nest submissions to the same executor. */
            networkExecutor = ExecutorHelper.getNetworkRequestExecutorInstance(); // weather, loc
            cachingExecutor = ExecutorHelper.getDatabaseExecutorInstance(); // all caching into db
            serviceExecutor = ExecutorHelper.getServiceExecutorInstance(); // for user purposes
            scheduledExecutor = ExecutorHelper.getScheduledPoolInstance();
            gpsExecutor = ExecutorHelper.getGpsExecutorInstance();

            /* (blocking) prep the default location data; clears the location database */
            defaultMonitorLocationList = new ArrayList<>();
            defaultHomeLocation = new MonitorLocation("298198", "Belgrade",
                    "44.8125", "20.4612", false, 0);
            /* new MonitorLocation("298486", "Novi Sad", "45.267136", "19.833549", false, 1); */
            defaultMonitorLocationList.add(0, defaultHomeLocation);
            Future<String> defaultLocationTask = cachingExecutor
                    .submit(new CacheDataInDbsTask(defaultHomeLocation, locationDaoReference));
            try {
                defaultLocationTask.get();
                Log.d(TAG, "defaultLocationTask complete!");
            } catch (Exception e) {
                Log.d(TAG, "defaultLocationTask - FATAL: default location db not set up.");
                e.printStackTrace();
            }

            /* location network operations */
            ArrayList<String> gpsLatLon = getGpsLatLon();
            MonitorLocation fetchedMonitorLocation = null;
            if (gpsLatLon != null) {
                fetchedMonitorLocation = getLocationFromNetwork(0, gpsLatLon,
                        defaultMonitorLocationList);
                cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation,
                        locationDaoReference));
            } else {
                Log.d(TAG, "getInstance: gpsLatLon is null; default to "
                        + defaultHomeLocation.getLocalizedName());
            }

            /* setup tasks at intervals (in secs) for data aging, visibility, and deletion policy */
            setUpPeriodicWeatherQueries(howLongVisible, howLongStored);
        }
        return instance;
    }

    /* public method for user-prompted update of instant sensor reading */
    /* (not implemented) offer an option to get instant sensor reading of humidity */
    public static synchronized void updateSensorReadingOnPrompt() {
        serviceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                List<Weather> sensorWeatherList = getForecastFromNetwork(TEMPERATURE_SENSOR_INSTANT,
                        defaultHomeLocation,"successfully obtained instant sensor " +
                                "temperature from LAN or ngrok URL.");
                if (sensorWeatherList != null) {
                    setAnalyticsToWeatherData(sensorWeatherList,
                            defaultHomeLocation.getLocalizedName(), UNDER_48H, TEMPERATURE_SENSOR_INSTANT);
                    String hms = sensorWeatherList.get(0).getTime().substring(11, 19);
                    /* modify LiveData visible in MainActivity */
                    instantSensorReading.postValue("["+sensorWeatherList.get(0).getCelsius()
                            +"]C, ["+hms+"]");
//                    sensorWeatherList.get(0).getHumidity();
                } else {
                    Log.i(TAG, "updateSensorReadingOnPrompt: sensor data returns null");
                    instantSensorReading.postValue("XXXXXXXXXXXXXXXXXXX");
                }
            }
        });
    }

    /* public method for user-prompted location update */
    public static synchronized void updateLocationOnPrompt() {
        serviceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> gpsLatLon = getGpsLatLon(); // blocking
                MonitorLocation fetchedMonitorLocation = null;
                if (gpsLatLon != null) {
                    fetchedMonitorLocation = getLocationFromNetwork(0, gpsLatLon,
                            defaultMonitorLocationList); // blocking
                    if (fetchedMonitorLocation != null) {
                        cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation,
                                locationDaoReference)); // nonblocking
                    }
                }
            }
        });
    }

    /* GPS latitude and longitude task */
    public static synchronized ArrayList<String> getGpsLatLon() {
        ArrayList<String> gpsLatLon = null;
        Future<ArrayList<String>> gpsLatLonTask = gpsExecutor
                .submit(new GetGpsTask(applicationFromRepository));
        try {
            gpsLatLon = gpsLatLonTask.get(3500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.i(TAG, "getCurrentLocation() at GPS task: TimeoutException");
            t.printStackTrace();
        } catch (ExecutionException | InterruptedException ei) {
            Log.i(TAG, "getCurrentLocation() at GPS task: other Exception");
            ei.printStackTrace();
        }

        return gpsLatLon;
    }

    /* Accuweather API query to get actual location key */
    public static synchronized MonitorLocation getLocationFromNetwork(Integer locationType,
                                                                      ArrayList<String> gpsLatLon,
                                                                      List<MonitorLocation> defaultMonitorLocationList) {
        String locationResponse;
        URL locationUrl;
        MonitorLocation fetchedMonitorLocation = defaultMonitorLocationList.get(locationType);
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = networkExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
        try {
            locationResponse = initialLocationTask.get(4500, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: obtained location response from Accuweather");
            fetchedMonitorLocation = ParseUtils.parseLocationJSON(locationResponse);
            fetchedMonitorLocation.setGpsAvailable(true); // if fetched by gps
            fetchedMonitorLocation.setLocationType(locationType);
        } catch (TimeoutException t) {
            t.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Timeout; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
        } catch (ExecutionException | InterruptedException ei) {
            ei.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
        }

        return fetchedMonitorLocation;
    }

    public static synchronized List<Weather> getForecastFromNetwork(Integer forecastType,
                                                                    MonitorLocation location,
                                                                    String callerMessage) {
        String weatherResponse;
        List<Weather> weatherList = null;

        /* forecastType: 0, 12hour; 1, 1hour; 2, sensor 1hour; 3, sensor current reading */
        URL networkWeatherUrl = NetworkUtils.buildUrlForWeather(forecastType, location.getLocation());
        Future<String> initialWeatherTask = networkExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(networkWeatherUrl));
        try {
            weatherResponse = initialWeatherTask.get(5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.d(TAG, "getForecastFromNetwork: timeout, forecast not obtained ");
            t.printStackTrace();
            return null;
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "getForecastFromNetwork: execution or interruption exceptions");
            e.printStackTrace();
            return null;
        }

        if (weatherResponse != null) {
            Log.d(TAG, "getForecastFromNetwork: " + callerMessage);
            weatherList = ParseUtils.parseWeatherJSON(weatherResponse);
        } else {
            return null;
        }

        return weatherList;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static Integer updateDataAge(long weatherTimeInMillis, long currentDateMillis,
                                         Integer howLongVisible, Integer howLongStored) {
        boolean printTimeData = false;
        /* do time comparison */
        if (weatherTimeInMillis >= currentDateMillis) {
            if (printTimeData) {
                Log.d(TAG, "updateDataAge: data point in future, data time ["
                        + weatherTimeInMillis + "] > current time [" + currentDateMillis
                        + "]; remain visible.");
                }
            return UNDER_48H;
        }

        if ( (currentDateMillis - weatherTimeInMillis) >= (howLongStored*1000)) {
            if (printTimeData) {
                Log.d(TAG, "updateDataAge: data point [" + weatherTimeInMillis +
                        "] older than current time [" + currentDateMillis + "] by more than " +
                        "howLongStored ["+ howLongStored + "]*1000, " +
                        "(should be over a week), don't store.");
                }
            return MORE_THAN_A_WEEK;
        }

        if ( (currentDateMillis - weatherTimeInMillis) >= (howLongVisible*1000)) {
            if (printTimeData) {
                Log.d(TAG, "updateDataAge: this point[" + weatherTimeInMillis + "] is older " +
                        "than current time ["+ currentDateMillis + "] by more than howLongVisible "
                        + howLongVisible+ "*1000, (should be 48H), not visible, but still stored.");
                }
            return BETWEEN_48H_AND_WEEK;
        }

        if (printTimeData) {
            Log.d(TAG, "updateDataAge: this point is in the last 48h; remain visible.");
        }

        return UNDER_48H;
    }

    /* interprets time in format used by Accuweather API */
    public static long getWeatherDataPointTime(String dateTime) {
        String pattern = "yyyy-MM-dd'T'HH:mm:ssXXX";
        SimpleDateFormat dateFormat; Date d = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            dateFormat = new SimpleDateFormat(pattern);
        } else {
            Log.d(TAG, "updateDataAge: can't update due to android version; default to 0");
            return 0;
        }
        try {
            d = dateFormat.parse(dateTime);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return d.getTime();
    }

    /* scheduled tasks in the background */
    public static synchronized void setUpPeriodicWeatherQueries(Integer howLongVisible,
                                                                Integer howLongStored) {
        Log.i(TAG, "setUpPeriodicWeatherQueries: to be set up.");
        /* define initial delay of each task; they should not compete for the same thread. */
        Integer initialDelayHourly = 10;
        Integer initialDelayTwelveHour = 20;
        Integer initialDelayMaintenance = 5;

        /* set up the hourly weather query; should cancel if no response */
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: scheduled Runnable executing hourly weather query. ");
                MonitorLocation fetchedLocation = defaultHomeLocation;

                /* (blocking) query the location db for relevant location */
                Future<MonitorLocation> getLocationFromDbTaskMethod = getLocationFromDbNonBlocking();
                try {
                    fetchedLocation = getLocationFromDbTaskMethod.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: location not fetched from database."
                            + " default to " + defaultMonitorLocationList.get(0).getLocalizedName());
                    e.printStackTrace();
                }

                /* candidate for general wrapper method; similar code used in other runnables */
                if (dataNeedsFetching(SINGLE_HOUR_DATA, fetchedLocation)) {
                    List<Weather> hourlyWeatherList = getForecastFromNetwork(SINGLE_HOUR_DATA,
                            fetchedLocation, "successfully fetched single hr forecast.");

                    if (hourlyWeatherList != null) {
                        setAnalyticsToWeatherData(hourlyWeatherList, fetchedLocation.getLocalizedName(),
                                UNDER_48H, SINGLE_HOUR_DATA);
                        cachingExecutor.submit(new CacheDataInDbsTask(hourlyWeatherList.get(0),
                                weatherDaoReference,false));
                    } else {
                        Log.i(TAG, "scheduled task: temperature sensor data returns as null");
                    }
                }

                /* temperature sensor fetching, should also be scheduled (check if json read): */
                if (dataNeedsFetching(TEMPERATURE_SENSOR, defaultHomeLocation)) {
                    List<Weather> sensorWeatherList = getForecastFromNetwork(TEMPERATURE_SENSOR,
                            defaultHomeLocation,
                            "successfully obtained hourly sensor temperature from LAN " +
                                    "or ngrok URL.");

                    if (sensorWeatherList != null) {
                        setAnalyticsToWeatherData(sensorWeatherList,
                                defaultHomeLocation.getLocalizedName(), UNDER_48H, TEMPERATURE_SENSOR);
                        cachingExecutor.submit(new CacheDataInDbsTask(sensorWeatherList.get(0),
                                weatherDaoReference,false));
                    } else {
                        Log.i(TAG, "scheduled task: temperature sensor data returns as null");
                    }
                }

            }
        }, initialDelayHourly, 1200, TimeUnit.SECONDS);

        /* set up the 12 hour weather query; may be done twice a day*/
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: scheduled Runnable executing hourly weather query. ");
                MonitorLocation fetchedLocation = defaultHomeLocation;

                /* (blocking) query the location db for relevant location */
                Future<MonitorLocation> getLocationFromDbTaskMethod = getLocationFromDbNonBlocking();
                try {
                    fetchedLocation = getLocationFromDbTaskMethod.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: location not fetched from db."
                            + " default to " + defaultMonitorLocationList.get(0).getLocalizedName());
                    e.printStackTrace();
                }

                /* weather operations done only if weather not fetched for this period */
                if (dataNeedsFetching(TWELVE_HOURS_DATA, fetchedLocation)) {
                    /* (blocking) get initial forecast (type 12h) based on initial location */
                    List<Weather> initialWeatherList = getForecastFromNetwork(TWELVE_HOURS_DATA,
                            fetchedLocation, "successfully obtained 12 hour forecast.");

                    /* (non-blocking)  cache the weather here, erase old data of initial forecast */
                    if (initialWeatherList != null) {
                        /* go through point(s) in the list and set initial analytics */
                        setAnalyticsToWeatherData(initialWeatherList,
                                fetchedLocation.getLocalizedName(), UNDER_48H, TWELVE_HOURS_DATA);
                        cachingExecutor.submit(new CacheDataInDbsTask(initialWeatherList,
                                weatherDaoReference,false, false));
                    }
                }
            }
        }, initialDelayTwelveHour, 3600*12, TimeUnit.SECONDS); // period 60*60*12, half day

        /* set up the daily weather database maintenance (do one for location too?) */
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                maintainWeatherDatabase(howLongVisible, howLongStored);
                clearOldWeatherData(); /* maybe just do this once a week */
            }
        }, initialDelayMaintenance, 3600, TimeUnit.SECONDS); // period 60*60*24, one day

    }

    public static Future<MonitorLocation> getLocationFromDbNonBlocking() {
        Future<MonitorLocation> getLocationFromDbTask = cachingExecutor
                .submit(new Callable<MonitorLocation>() {
            @Override
            public MonitorLocation call() throws Exception {
                List<MonitorLocation> locationListNonLive =
                        locationDaoReference.getLocationTableNonLive();
                if (locationListNonLive != null){
                    Log.d(TAG, "setUpPeriodicWeatherQueries: locationListNonLive: "
                            +locationListNonLive.get(0).getLocalizedName());
                } else {
                    Log.d(TAG, "FATAL: setUpPeriodicWeatherQueries: querying locationDb " +
                            "with non-LiveData method does not return an entry");
                    return defaultMonitorLocationList.get(0);
                }
                return locationListNonLive.get(0);
            }
        });

        return getLocationFromDbTask;
    }

    public static Future<List<Weather>> getForecastFromDbNonBlocking() {
        Future<List<Weather>> getWeatherListFromDbTask = cachingExecutor
                .submit(new Callable<List<Weather>>() {
            @Override
            public List<Weather> call() throws Exception {
                List<Weather> weatherListNonLive = weatherDaoReference.getAllWeatherPointsNonLive();
                if (weatherListNonLive != null){
//                    Log.d(TAG, "getForecastFromDbNonBlocking: list size: "
//                            +weatherListNonLive.size());
                    return weatherListNonLive;
                } else {
                    Log.d(TAG, "FATAL: getForecastFromDbNonBlocking: querying locationDb " +
                            "with non-LiveData method does not return an entry");
                    return null;
                }
            }
        });

        return getWeatherListFromDbTask;
    }

    /* data maintenance methods */
    /* database maintenance to be called in a runnable object as a background task */
    private static void maintainWeatherDatabase(Integer howLongVisible, Integer howLongStored) {
        /* (blocking) query the weather db for entire list by submitting to caching thread */
        List<Weather> weatherList = null;
        Future<List<Weather>> getWeatherListFromDbTaskMethod = getForecastFromDbNonBlocking();
        try {
            weatherList = getWeatherListFromDbTaskMethod.get();
        } catch (Exception e) {
            Log.d(TAG, "maintainWeatherDatabase: exception; weather data not fetched. ");
            e.printStackTrace();
        }
        if (weatherList == null) {
            Log.d(TAG, "maintainWeatherDatabase: data not fetched; no maintenance done. ");
            return;
        }

        /* examine each data point's DateTime, formulate Date object, compare to current, modify age
         * category, and mark old data for deletion if older than howLongStored */
        boolean printWeatherInfo = false;
        List<Weather> modifiedWeatherList = new ArrayList<>();
        Integer ageCategory = UNDER_48H;
        long weatherTimeInMillis;
        Date currentDate = new Date(System.currentTimeMillis());
        long currentDateMillis = currentDate.getTime();

        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            Integer elementIndex = weatherList.indexOf(weatherEntryInIter);

            if (printWeatherInfo) {
                Log.i(TAG, "\nElement index: " + elementIndex
//                    + "\nType: (0 is 12hr, 1 is 1hr type): " + weatherEntryInIter.getCategory()
                        + "\nDateTime: " + weatherEntryInIter.getTime()
//                    + "\nID: " + weatherEntryInIter.getId()
                        + "\nPersistence: " + weatherEntryInIter.getPersistence());
            }

            /* set the persistence and the time in millis for this point */
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                weatherTimeInMillis = weatherEntryInIter.getTimeInMillis();
                ageCategory = updateDataAge(weatherTimeInMillis, currentDateMillis, howLongVisible,
                        howLongStored);
            }
            weatherEntryInIter.setPersistence(ageCategory);
            modifiedWeatherList.add(elementIndex, weatherEntryInIter);
        }
        Log.i(TAG, "maintainWeatherDatabase: maintenance should be done; cache it.");

        /* store the weather list back into the database by iteratively updating each data point */
        cachingExecutor.submit(new CacheDataInDbsTask(modifiedWeatherList, weatherDaoReference,
                false, true));
    }

    /* */
    public static void clearOldWeatherData() {
        /* (blocking) query the weather db for entire list, via caching executor */
        List<Weather> weatherList = getWeatherDataEntriesFromDb();
//        Future<List<Weather>> getWeatherListFromDbTaskMethod = getForecastFromDbNonBlocking();
//        try {
//            weatherList = getWeatherListFromDbTaskMethod.get();
//        } catch (Exception e) {
//            Log.d(TAG, "clearOldWeatherData: exception; weather data not fetched. ");
//            e.printStackTrace();
//        }
//        if (weatherList == null) {
//            Log.d(TAG, "clearOldWeatherData: data not fetched; no deletion to be done. ");
//            return;
//        }

        boolean printWeatherInfo = false;
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            Integer elementIndex = weatherList.indexOf(weatherEntryInIter);
            if (printWeatherInfo) {
                Log.i(TAG, "\nElement index: " + elementIndex
                        + "\nPoint type: (0 is 12hr, 1 is 1hr): " + weatherEntryInIter.getCategory()
                        + "\nDateTime: " + weatherEntryInIter.getTime()
                        + "\nID: " + weatherEntryInIter.getId()
                        + "\nPersistence: " + weatherEntryInIter.getPersistence());
            }

            if (weatherEntryInIter.getPersistence() == MORE_THAN_A_WEEK) {
                weatherDaoReference.delete(weatherEntryInIter);
            }
        }
    }

    /* checks if forecast of a type, for the time period, in a location, are in the db */
    private static boolean dataNeedsFetching(Integer dataCategory, MonitorLocation location) {
        Log.d(TAG, "dataNeedsFetching: check if dataCategory: "
                +dataCategory+"; needs fetching. 1: hourly, 0: twelve hours.");
        List<Weather> weatherList = null;

        /* get start of current hour */
        Calendar today = Calendar.getInstance(); // .getInstance(TimeZone.getTimeZone("Belgrade"));
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MINUTE, 0);
        long startOfHour = today.getTimeInMillis(); // add 1hr for +01:00 ?
        long startOfHourNext = startOfHour + 3600000;
        Log.d(TAG, "dataNeedsFetching: current hour millis: "+startOfHour);

        Future<List<Weather>> checkDataTask = getForecastFromDbNonBlocking();
        try {
            weatherList = checkDataTask.get();
        } catch (Exception e){
            e.printStackTrace();
            return true;
        }

        String locationName = location.getLocalizedName();
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();

            /* for API forecasts, the next hour is checked */
            if (dataCategory == SINGLE_HOUR_DATA || dataCategory == TWELVE_HOURS_DATA) {
                if ((weatherEntryInIter.getCategory() == dataCategory)
                        && (weatherEntryInIter.getTimeInMillis() == startOfHourNext)
                        && (weatherEntryInIter.getLocation().equals(locationName))) {
                    Log.d(TAG, "dataNeedsFetching for this hour: NO, weather data type(API " +
                            "forecast):" + dataCategory + "location:|" + locationName
                            + "| is already present in db");
                    return false;
                }

                /* for the sensor, the current hour is checked */
            } else if (dataCategory == TEMPERATURE_SENSOR) {
                if ((weatherEntryInIter.getCategory() == dataCategory)
                        && (weatherEntryInIter.getTimeInMillis() == startOfHour)
                        && (weatherEntryInIter.getLocation().equals(locationName))) {
                    Log.d(TAG, "dataNeedsFetching for this hour: NO, weather data type (sensor):"
                            + dataCategory + "location:|" + locationName + "| is present in db");
                    return false;
                }
            } else {} /* other potential options */

        }
        Log.d(TAG, "dataNeedsFetching for this hour: YES, weather data type:"
                +dataCategory+"location:"+locationName+ " not present in db");
        return true;
    }

    /* prepares data about to be inserted into database */
    private static void setAnalyticsToWeatherData(List<Weather> weatherList, String localizedName,
                                                  Integer persistence, Integer category) {
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            if (localizedName != null) {
                weatherEntryInIter.setLocation(localizedName);
            }
            if (persistence != null) {
                weatherEntryInIter.setPersistence(persistence);
            }
            if (category != null) {
                weatherEntryInIter.setCategory(category);
            }

            long weatherTimeInMillis = getWeatherDataPointTime(weatherEntryInIter.getTime());
            if (weatherTimeInMillis == 0) {
                Log.d(TAG, "setAnalyticsToWeatherData: ERROR: " +
                        "MILLIS DEFAULT TO 0, API FORMAT NOT READ.");
            }
            weatherEntryInIter.setTimeInMillis(weatherTimeInMillis);
        }
    }

    public static List<Weather> getWeatherDataEntriesFromDb() {
        /* (blocking) query the weather db for entire list, via caching executor */
        List<Weather> weatherList = null;
        Future<List<Weather>> getWeatherListFromDbTaskMethod = getForecastFromDbNonBlocking();
        try {
            weatherList = getWeatherListFromDbTaskMethod.get();
        } catch (Exception e) {
            Log.d(TAG, "getWeatherDataEntriesFromDb: exception; weather data not fetched. ");
            e.printStackTrace();
        }
        if (weatherList == null) {
            Log.d(TAG, "getWeatherDataEntriesFromDb: data returns null ");
        }
        return weatherList;
    }
}

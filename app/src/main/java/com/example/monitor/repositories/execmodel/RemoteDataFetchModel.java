package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.MonitorConstants;
import com.example.monitor.MonitorEnums;
import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.networkutils.TopicData;
import com.example.monitor.repositories.parseutils.ParseUtils;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/* Background execution module. Takes care of:
* fetching location information from android GPS module,
* fetching "location code" and weather data from Accuweather API
* formatting and storing data in location and weather database
* maintaining data stored in the weather database
* */
public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel";

    private static MutableLiveData<String> instantSensorReading;

    private static RemoteDataFetchModel instance;
    private static WeatherDao weatherDaoReference;
    private static LocationDao locationDaoReference;
    private static Application applicationFromRepository;
    private static MonitorLocation defaultHomeLocation;
    private static List<MonitorLocation> defaultMonitorLocationList;
    private static LocationManager locationManager;

    private static ExecutorService networkExecutor;
    private static ExecutorService cachingExecutor;
    private static ExecutorService serviceExecutor;
    private static ExecutorService gpsExecutor;
    private static ScheduledExecutorService scheduledExecutor;

    /* mqtt */
    private static Mqtt5Client mqtt5Client;

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
            locationManager = (LocationManager) applicationFromRepository
                    .getSystemService(Context.LOCATION_SERVICE);

            /* instantiate all necessary executors; never nest submissions to the same executor. */
            networkExecutor = ExecutorHelper.getNetworkRequestExecutorInstance(); // weather, loc
            cachingExecutor = ExecutorHelper.getDatabaseExecutorInstance(); // all caching into db
            serviceExecutor = ExecutorHelper.getServiceExecutorInstance(); // for user purposes
            scheduledExecutor = ExecutorHelper.getScheduledPoolInstance();
            gpsExecutor = ExecutorHelper.getGpsExecutorInstance();

            /* mqtt reference to client */
            mqtt5Client = MQTTConnection.getClient();

            /* (blocking) prep the default location data; clears the location database */
            defaultMonitorLocationList = new ArrayList<>();
            defaultHomeLocation = new MonitorLocation("298198", "Belgrade",
                    "44.8125", "20.4612", false, 0);
            defaultMonitorLocationList.add(0, defaultHomeLocation);
            Future<String> defaultLocationTask = cachingExecutor
                    .submit(new CacheDataInDbsTask(defaultHomeLocation, locationDaoReference));
            try {
                defaultLocationTask.get();
            } catch (Exception e) {
                Log.d(TAG, "defaultLocationTask - FATAL: default location db not set up.");
                e.printStackTrace();
            }

            /* location network operations may also be done at startup */
            /* updateLocationOnPrompt(); */

            /* set up the hourly weather query; should cancel if no response */
            scheduleHourlyTasks();

            /* set up the 12 hour weather query; may be done twice a day*/
            scheduleDailyTasks();

            /* set up the daily weather database maintenance (do one for location too?) */
            scheduleMaintenanceTasks();
        }
        return instance;
    }

    /* public method for user-prompted update of instant sensor reading */
    /* (not implemented) offer an option to get instant sensor reading of brightness */
    public static synchronized void updateSensorReadingOnPrompt(String parameter) {

        /* check MQTT connection */
        mqtt5Client = MQTTConnection.getClient();
        if (!mqtt5Client.getState().isConnected()) {
            Toast.makeText(applicationFromRepository, "Client not connected to MQTT", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateSensorReadingOnPrompt: NO MQTT CONNECTION");
            instantSensorReading.postValue("V" + "OFFLINE" + ";T" + "OFFLINE"  + "|");
            MQTTConnection.connectAsync(); // try connecting
            return;
        }

        serviceExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (MonitorEnums.USE_NGROK) {
                    List<Weather> sensorWeatherList = getForecastFromNetwork(MonitorEnums.HOME_SENSOR_INSTANT,
                            defaultHomeLocation, "instant sensor data from ngrok.");
                    if (sensorWeatherList != null) {
                        String hms = sensorWeatherList.get(0).getTime().substring(11, 19);
                        instantSensorReading.postValue("V" + sensorWeatherList.get(0).getCelsius()
                                + ";T" + hms + "|");
                    } else {
                        Log.i(TAG, "updateSensorReadingOnPrompt: sensor data returns null");
                        instantSensorReading.postValue(MonitorConstants.SENSOR_READING_FORMAT);
                    }
                }

                if (MonitorEnums.USE_MQTT) {
                    String topic = TopicData.getJsonSensorInstantDataTopic();
                    mqtt5Client.toAsync().subscribeWith().topicFilter(topic)/*.qos(MqttQos.AT_LEAST_ONCE)*/
                        .callback(publish -> {
                            String hms;
                            String sensorValue;
                            List<Weather> weatherList = getDataListFromPayload(topic, publish);
                            if (weatherList == null) {
                                sensorValue = "OFFLINE";
                                hms = "OFFLINE";
                            } else {
                                Weather dataPoint = weatherList.get(0);
                                hms = dataPoint.getTime().substring(11, 19);
                                if (parameter.equals("Temperature")){
                                    sensorValue = dataPoint.getCelsius() + " C";
                                } else if (parameter.equals("Humidity")) {
                                    sensorValue = dataPoint.getHumidity()+ " %";
                                } else {
                                    Log.d(TAG, "updateSensorReadingOnPrompt: No valid parameter selected.");
                                    sensorValue = "N/A";
                                }
                            }

                            /* modify LiveData visible in MainActivity */
                            instantSensorReading.postValue("V" + sensorValue + ";T" + hms + "|");
                        }).send();
                }
            }
        });
    }

    /* criterium for sensors being online is that they are timestamped within the last 10 minutes */
    private static boolean areSensorsOnline(long currentTime, long dataTime) {
        Log.d(TAG, "areSensorsOnline: currentTime: "+currentTime+", dataTime: "+dataTime);
        if (Math.abs(currentTime-dataTime) < MonitorConstants.TEN_MINUTES) {
            Log.d(TAG, "areSensorsOnline: sensors are online");
            return true;
        }
        Log.d(TAG, "areSensorsOnline: sensors are offline");
        return false;
    }

    /*** location routines ***/
    /* public method for user-prompted location update */
    public static synchronized void updateLocationOnPrompt() {
        if (!(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))) {
            Toast.makeText(applicationFromRepository,
                    "GPS not available", Toast.LENGTH_SHORT).show();
            return;
        }
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
            gpsLatLon = gpsLatLonTask.get(MonitorConstants.STD_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.i(TAG, "getCurrentLocation() at GPS task: Exception");
            e.printStackTrace();
        }

        return gpsLatLon;
    }

    /* Accuweather API query to get actual location key */
    public static synchronized MonitorLocation getLocationFromNetwork(Integer locationType,
                                                                      ArrayList<String> gpsLatLon,
                                                                      List<MonitorLocation> defaultMonitorLocationList) {
        MonitorLocation fetchedMonitorLocation = defaultMonitorLocationList.get(locationType);
        URL locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = networkExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
        try {
            String locationResponse = initialLocationTask.get(MonitorConstants.STD_TIMEOUT,
                    TimeUnit.MILLISECONDS);
            fetchedMonitorLocation = ParseUtils.parseLocationJSON(locationResponse);
            fetchedMonitorLocation.setGpsAvailable(true); // if fetched by gps
            fetchedMonitorLocation.setLocationType(locationType);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
        }
        return fetchedMonitorLocation;
    }

    /*** network forecast tasks ***/
    /* contact accuweather API */
    public static synchronized List<Weather> getForecastFromNetwork(Integer forecastType,
                                                                    MonitorLocation location,
                                                                    String callerMessage) {
        String weatherResponse;
        List<Weather> weatherList = null;
        URL networkWeatherUrl = NetworkUtils.buildUrlForWeather(forecastType, location.getLocation());
        Future<String> initialWeatherTask = networkExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(networkWeatherUrl));
        try {
            weatherResponse = initialWeatherTask.get(MonitorConstants.STD_TIMEOUT,
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.d(TAG, "getForecastFromNetwork: exception for " + callerMessage);
            e.printStackTrace();
            return null;
        }

        if (weatherResponse != null) {
            Log.d(TAG, "getForecastFromNetwork: successfully obtained " + callerMessage);
            weatherList = ParseUtils.parseWeatherJSON(weatherResponse);
        } else {
            return null;
        }

        return weatherList;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static Integer updateDataAge(long weatherTimeInMillis, long currentDateMillis,
                                         Integer howLongVisible, Integer howLongStored) {
        /* do time comparison */
        if (weatherTimeInMillis >= currentDateMillis) {
            return MonitorEnums.UNDER_48H;
        }

        if ( (currentDateMillis - weatherTimeInMillis) >= (howLongStored*1000)) {
            return MonitorEnums.MORE_THAN_A_WEEK;
        }

        if ( (currentDateMillis - weatherTimeInMillis) >= (howLongVisible*1000)) {
            return MonitorEnums.BETWEEN_48H_AND_WEEK;
        }

        return MonitorEnums.UNDER_48H;
    }

    /* interprets time in format used by Accuweather API */
    public static long getWeatherDataPointTime(String dateTime) {
        SimpleDateFormat dateFormat;
        Date d = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            dateFormat = new SimpleDateFormat(MonitorConstants.DATE_TIME_PATTERN);
        } else {
            Log.d(TAG, "getWeatherDataPointTime: Can't update due to android version; " +
                    "default to 0");
            return 0;
        }
        try {
            d = dateFormat.parse(dateTime);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return d.getTime();
    }


    /*** scheduled tasks in the background ***/
    public static void fetchDataType(Integer type, MonitorLocation loc, long time, String callerMsg) {
        if (dataNeedsFetching(type, loc, time)) {
            List<Weather> dataList = getForecastFromNetwork(type, loc, callerMsg);
            if (dataList != null) {
                setAnalyticsToData(dataList, loc.getLocalizedName(), MonitorEnums.UNDER_48H, type);
                if (fetchedDataMatches(type, dataList, time)) {
                    if (type == MonitorEnums.TWELVE_HOURS_DATA) {
                        cachingExecutor.submit(new CacheDataInDbsTask(dataList, null,
                                weatherDaoReference, false, false));
                    } else {
                        cachingExecutor.submit(new CacheDataInDbsTask(null, dataList.get(0),
                                weatherDaoReference, false, false));
                    }
                } else {
                    Log.d(TAG, "fetchDataType: following data did not match: " + callerMsg);
                }
            } else {
                Log.i(TAG, "fetchDataType: following data is null: " + callerMsg);
            }
        }

    }

    private static synchronized void scheduleMaintenanceTasks() {
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                maintainWeatherDatabase(MonitorConstants.VISIBILITY_DURATION, MonitorConstants.STORAGE_DURATION);
                clearOldWeatherData(); /* maybe just do this once a week */
            }
        }, MonitorConstants.INITIAL_DELAY_MAINTENANCE, MonitorConstants.PERIODIC_DELAY_MAINTENANCE, TimeUnit.SECONDS);
    }

    private static synchronized void scheduleDailyTasks() {
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                MonitorLocation fetchedLocation = defaultHomeLocation;
                long startOfHour = getCurrentMillis();

                /* if no internet connection verifiable, skip all scheduled tasks */
                if (applicationFromRepository != null) {
                    if (!isConnectingToInternet(applicationFromRepository.getApplicationContext())) {
                        return;
                    }
                } else {return;}

                /* (blocking) query the location db for relevant location */
                Future<MonitorLocation> getLocationFromDbTaskMethod = getLocationFromDbNonBlocking();
                try {
                    fetchedLocation = getLocationFromDbTaskMethod.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                fetchDataType(MonitorEnums.TWELVE_HOURS_DATA, fetchedLocation, startOfHour,
                        "API 12hr forecast.");

            }
        }, MonitorConstants.INITIAL_DELAY_TWELVE_HOURS, MonitorConstants.PERIODIC_DELAY_TWELVE_HOURS, TimeUnit.SECONDS);
    }

    private static synchronized void scheduleHourlyTasks() {
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                MonitorLocation fetchedLocation = defaultHomeLocation; // default if nothing in db
                long startOfHour = getCurrentMillis();

                /* if no internet connection verifiable, skip all scheduled tasks */
                if (applicationFromRepository != null) {
                    if (!isConnectingToInternet(applicationFromRepository.getApplicationContext())) {
                        return;
                    }
                } else {return;}

                /* (blocking) query the location db for relevant location */
                Future<MonitorLocation> getLocationFromDbTaskMethod = getLocationFromDbNonBlocking();
                try {
                    fetchedLocation = getLocationFromDbTaskMethod.get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }

                /* single hour from Accuweather API */
                fetchDataType(MonitorEnums.SINGLE_HOUR_DATA, fetchedLocation, startOfHour,
                        "API single hr forecast.");

                /* single hour from sensors, ngrok home server */
                if (MonitorEnums.USE_NGROK) {
                    fetchDataType(MonitorEnums.HOME_SENSOR, defaultHomeLocation, startOfHour,
                            "1hr sensor temperature from ngrok.");
                }

                /* single hour from sensors, MQTT */
                if (MonitorEnums.USE_MQTT) {
                    if (dataNeedsFetching(MonitorEnums.HOME_SENSOR, defaultHomeLocation, startOfHour)) {
                        // issue: if the last retained is hours ago, it will still be added; should be for current hour
                        String topic = TopicData.getJsonSensorHourlyDataTopic();
                        mqtt5Client = MQTTConnection.getClient();
                        mqtt5Client.toAsync().subscribeWith().topicFilter(topic)/*.qos(MqttQos.AT_LEAST_ONCE)*/
                            .callback(publish -> {
                                List<Weather> sensorWeatherList = getDataListFromPayload(topic, publish);
                                if (sensorWeatherList != null) {
                                    setAnalyticsToData(sensorWeatherList,
                                            defaultHomeLocation.getLocalizedName(),
                                            MonitorEnums.UNDER_48H, MonitorEnums.HOME_SENSOR);
                                    if (fetchedDataMatches(MonitorEnums.HOME_SENSOR, sensorWeatherList, startOfHour)) {
                                        cachingExecutor.submit(new CacheDataInDbsTask(null, sensorWeatherList.get(0),
                                                weatherDaoReference, false, false));
                                    }
                                } else {
                                    Log.i(TAG, "task in mqtt callback: sensor data null");
                                }
                            }).send();
                    }
                }

            }
        }, MonitorConstants.INITIAL_DELAY_HOURLY, MonitorConstants.PERIODIC_DELAY_HOURLY, TimeUnit.SECONDS);
    }

    private static List<Weather> getDataListFromPayload(String topic, Mqtt5Publish publish) {
        String payloadString = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
        System.out.println("Successfully obtained mqtt data in callback;" +
                " topic: " + topic + ", payload: " + payloadString);
        mqtt5Client.toBlocking().unsubscribeWith().topicFilter(topic).send();
        List<Weather> list = ParseUtils.parseWeatherJSON(payloadString);

        // check if sensor timestamp matches current time in UTC+02:00
        if (!areSensorsOnline(System.currentTimeMillis()
                + MonitorConstants.TWO_HOURS, list.get(0).getTimeInMillis())) {
            return null;
        }
        return list;
    }

    /*  for 12-hr API fetch, will need to check first hour.
     * for 1-hr API fetch, check the hour against the "startOfNextHour" */
    private static boolean fetchedDataMatches(Integer type, List<Weather> list, long startOfHour) {
        if (type == MonitorEnums.HOME_SENSOR){
            return (list.get(0).getTimeInMillis() == startOfHour);
        } else { // data considered as matching by default for all API queries, for now
            return true;
        }
    }

    /*** data maintenance methods ***/
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

        Log.d(TAG, "maintainWeatherDatabase: running..");

        /* examine each data point's DateTime, formulate Date object, compare to current, modify age
         * category, and mark old data for deletion if older than howLongStored */
        List<Weather> modifiedWeatherList = new ArrayList<>();
        Integer ageCategory = MonitorEnums.UNDER_48H;
        long currentDateMillis = (new Date(System.currentTimeMillis())).getTime();

        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            Integer elementIndex = weatherList.indexOf(weatherEntryInIter);

            /* set the persistence and the time in millis for this point */
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                long weatherTimeInMillis = weatherEntryInIter.getTimeInMillis();
                ageCategory = updateDataAge(weatherTimeInMillis, currentDateMillis, howLongVisible,
                        howLongStored);
            }
            weatherEntryInIter.setPersistence(ageCategory);
            modifiedWeatherList.add(elementIndex, weatherEntryInIter);
        }
        /* store the weather list back into the database by iteratively updating each data point */
        cachingExecutor.submit(new CacheDataInDbsTask(modifiedWeatherList, null,
                weatherDaoReference,false, true));
    }

    public static void clearOldWeatherData() {
        /* (blocking) query the weather db for entire list, via caching executor */
        List<Weather> weatherList = getWeatherDataEntriesFromDb();
        boolean printWeatherInfo = true;
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

            if (weatherEntryInIter.getPersistence() == MonitorEnums.MORE_THAN_A_WEEK) {
                Log.d(TAG, "clearOldWeatherData: data to be deleted, age is more than a week");
                weatherDaoReference.delete(weatherEntryInIter);
            } else {
                Log.d(TAG, "clearOldWeatherData: no data to be deleted");
            }
        }
    }

    /* checks if forecast of a type, for the time period, in a location, are in the db */
    private static boolean dataNeedsFetching(Integer dataCategory, MonitorLocation location, long startOfHour) {
        Log.d(TAG, "dataNeedsFetching: check if dataCategory: " + dataCategory
                + "; needs fetching. 2: sensor, 1: hourly, 0: twelve hours.");
        List<Weather> weatherList = null;

        /* get start of next hour */
        long startOfHourNext = startOfHour + MonitorConstants.ONE_HOUR;

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
            if (dataCategory == MonitorEnums.SINGLE_HOUR_DATA
                    || dataCategory == MonitorEnums.TWELVE_HOURS_DATA) {
                if ((weatherEntryInIter.getCategory() == dataCategory)
                        && (weatherEntryInIter.getTimeInMillis() == startOfHourNext)
                        && (weatherEntryInIter.getLocation().equals(locationName))) {
                    Log.d(TAG, "dataNeedsFetching for this hour: NO");
                    return false;
                }

                /* for the sensor, the current hour is checked */
            } else if (dataCategory == MonitorEnums.HOME_SENSOR) {
                if ((weatherEntryInIter.getCategory() == dataCategory)
                        && (weatherEntryInIter.getTimeInMillis() == startOfHour)
                        && (weatherEntryInIter.getLocation().equals(locationName))) {
                    Log.d(TAG, "dataNeedsFetching for this hour: NO");
                    return false;
                }
            } else {} /* other potential options */

        }
        Log.d(TAG, "dataNeedsFetching for this hour: YES, for location:"+locationName);
        return true;
    }

    /* prepares data about to be inserted into database */
    private static void setAnalyticsToData(List<Weather> weatherList, String localizedName,
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

            // should be redundant as json data received has time in millis
            long weatherTimeInMillis = getWeatherDataPointTime(weatherEntryInIter.getTime());
            weatherEntryInIter.setTimeInMillis(weatherTimeInMillis);
        }
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

    private static long getCurrentMillis() {
        Calendar today = Calendar.getInstance(); // .getInstance(TimeZone.getTimeZone("Belgrade"));
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MINUTE, 0);
        long startOfHour = today.getTimeInMillis(); // add 1hr for +01:00 ?
        return startOfHour;
    }

    /*** for checking internet connectivity ***/
    public static boolean isConnectingToInternet(Context mContext) {
        if (mContext == null) {
            Log.d(TAG, "isConnectingToInternet: context argument is null; can't check status.");
            return false;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    final NetworkCapabilities nc = connectivityManager.getNetworkCapabilities(network);
                    boolean connxStatus = (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                    if (connxStatus) {
                        Log.d(TAG, "CONNECTED TO INTERNET");
                    } else {
                        Log.d(TAG, "NOT CONNECTED TO INTERNET");
                    }
                    return connxStatus;
                }
            } else {
                NetworkInfo[] networkInfos = connectivityManager.getAllNetworkInfo();
                for (NetworkInfo tempNetworkInfo : networkInfos) {
                    if (tempNetworkInfo.isConnected()) {
                        Log.d(TAG, "CONNECTED TO INTERNET");
                        return true;
                    }
                }
            }
        }
        Log.d(TAG, "NOT CONNECTED TO INTERNET");
        return false;
    }
}

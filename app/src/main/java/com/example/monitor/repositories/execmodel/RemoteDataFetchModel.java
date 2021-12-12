package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* threadpool types
*
* fixed thread pool: threads fetch, execute tasks from a synchronized queue.
*
* cached thread pool: synchronous queue can hold only one task. the pool looks for a free thread to
* do task; if no free thread available, it will create a new one, and kill threads idle for >60 secs
*
* scheduled thread pool: can schedule tasks to be done in a queue. tasks can be done at a delay, or
* at some frequency. the tasks are distributed in the pool based on when they should be executed.
*
* single threaded executor: size of pool is 1. remake thread killed due to exceptions. fetches and
* executes tasks submitted to queue sequentially. (n) task finishes before (n+1) taken by thread.
*
* */

public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel";

    private static final Integer SINGLE_HOUR_DATA = 1;
    private static final Integer TWELVE_HOURS_DATA = 0;

    private static final Integer UNDER_24H = 0;
    private static final Integer BETWEEN_DAY_AND_WEEK = 1;
    private static final Integer MORE_THAN_A_WEEK = 2;

    private static RemoteDataFetchModel instance;
    private static WeatherDao weatherDaoReference;
    private static LocationDao locationDaoReference;
    private static Application applicationFromRepository;
    private static MonitorLocation defaultHomeLocation;
    private static List<MonitorLocation> defaultMonitorLocationList;

    private static ExecutorService weatherExecutor;
    private static ExecutorService locationExecutor;
    private static ExecutorService cachingExecutor;
    private static ExecutorService serviceExecutor;
    private static ExecutorService gpsExecutor;
    private static ScheduledExecutorService scheduledExecutor;

    /* singleton, instantiated in environment which provides a data access object and reference to activity  */
    public static RemoteDataFetchModel getInstance(WeatherDao weatherDao, LocationDao locationDao, Application application) {
        if (instance == null){
            
            /* set up private members */
            instance = new RemoteDataFetchModel();
            weatherDaoReference = weatherDao;
            locationDaoReference = locationDao;
            applicationFromRepository = application;

            /* define scheduling parameters in seconds */
            Integer tillNextQuery = 40; /* should be an hour */
            Integer howLongVisible = 60 * 60 * 24; /* should be a day */
            Integer howLongStored = 60 * 60 * 24 * 7; /* should be a week */

            /* instantiate all necessary executors */
            weatherExecutor = ExecutorHelper.getNetworkRequestExecutorInstance(); // weather network
            locationExecutor = ExecutorHelper.getNetworkRequestExecutorInstance(); // location network
            cachingExecutor = ExecutorHelper.getDatabaseExecutorInstance(); // all caching into db
            serviceExecutor = ExecutorHelper.getServiceExecutorInstance(); // for user purposes
            scheduledExecutor = ExecutorHelper.getScheduledPoolInstance();
            gpsExecutor = ExecutorHelper.getGpsExecutorInstance();

            // ----- startup tasks
            /* (blocking) prep the default location data; clears the location database */
            defaultMonitorLocationList = new ArrayList<>();
            defaultHomeLocation = new MonitorLocation("298198", "Belgrade",
                    "44.8125", "20.4612", false, 0);
            /* new MonitorLocation("298486", "Novi Sad", "45.267136", "19.833549", false, 1); */
            defaultMonitorLocationList.add(0, defaultHomeLocation);
            Future<String> defaultLocationTask = cachingExecutor.submit(new CacheDataInDbsTask(defaultHomeLocation, locationDaoReference));
            try {
                defaultLocationTask.get();
                Log.d(TAG, "defaultLocationTask complete!");
            } catch (Exception e) {
                Log.d(TAG, "defaultLocationTask - FATAL: location database couldn't be set up with default locations.");
                e.printStackTrace();
            }

            /* location operations */
            ArrayList<String> gpsLatLon = getGpsLatLon();
            MonitorLocation fetchedMonitorLocation = null;
            if (gpsLatLon != null) {
                fetchedMonitorLocation = getLocationFromNetwork(0, gpsLatLon, defaultMonitorLocationList);
                cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation, locationDaoReference));
            } else {
                Log.d(TAG, "getInstance: gpsLatLon is null; default to "+defaultHomeLocation.getLocalizedName());
                fetchedMonitorLocation = defaultHomeLocation;
            }

            /* weather operations */
            /* (blocking) get initial forecast (type 12h) based on initial location */
            List<Weather> initialWeatherList = getForecastFromNetwork(TWELVE_HOURS_DATA,
                    fetchedMonitorLocation, "successfully obtained 12 hour forecast.");

            /* (non-blocking) then cache the weather here and erase old data in case of initial forecast */
            if (initialWeatherList != null) {
                /* go through point(s) in the list and set initial analytics */
                setAnalyticsToWeatherData(initialWeatherList, fetchedMonitorLocation.getLocalizedName(), UNDER_24H, TWELVE_HOURS_DATA);
                cachingExecutor.submit(new CacheDataInDbsTask(initialWeatherList, weatherDaoReference,
                        false, false));
            }
            // ----- end startup tasks



            // ----- periodic tasks: require RxJava(?) to operate on data in the background
            /* periodic request. pass: request frequency, data aging and visibility, deletion policy */
            setUpPeriodicWeatherQueries(tillNextQuery, howLongVisible, howLongStored); /* intervals in seconds */
            // ----- end periodic tasks
        }
        return instance;
    }

    /* public method for user-prompted location update */
    public static synchronized void updateLocationOnPrompt() {
        serviceExecutor.submit(new Runnable() {
            @Override
            public void run() {

                Log.d(TAG, "run: submitted runnable for GPS user request.");
                ArrayList<String> gpsLatLon = getGpsLatLon(); // blocking
                MonitorLocation fetchedMonitorLocation = null;
                if (gpsLatLon != null) {
                    fetchedMonitorLocation = getLocationFromNetwork(0, gpsLatLon, defaultMonitorLocationList); // blocking
                    if (fetchedMonitorLocation != null) {
                        cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation, locationDaoReference)); // nonblocking
                    }
                }

            }
        });
    }

    /* decoupling getCurrentLocation; GPS task */
    public static synchronized ArrayList<String> getGpsLatLon() {
        ArrayList<String> gpsLatLon = null;
        Future<ArrayList<String>> gpsLatLonTask = gpsExecutor.submit(new GetGpsTask(applicationFromRepository));
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
    public static synchronized MonitorLocation getLocationFromNetwork(Integer locationType, ArrayList<String> gpsLatLon, List<MonitorLocation> defaultMonitorLocationList) {
        String locationResponse;
        URL locationUrl;
        MonitorLocation fetchedMonitorLocation = defaultMonitorLocationList.get(locationType);
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = locationExecutor.submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
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
            Log.d(TAG, "getCurrentLocation at accuweather query: other Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
        }

        return fetchedMonitorLocation;
    }

    public static synchronized List<Weather> getForecastFromNetwork(Integer forecastType, MonitorLocation location, String callerMessage) {
        String weatherResponse;
        List<Weather> weatherList;

        /* forecastType: 0, 12hour; 1, 1hour. */
        Log.d(TAG, "getForecastFromNetwork: building URL with location "+location.getLocation());
        URL networkWeatherUrl = NetworkUtils.buildUrlForWeather(forecastType, location.getLocation());
        Future<String> initialWeatherTask = weatherExecutor
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
        Log.d(TAG, "getForecastFromNetwork: "+callerMessage);
        weatherList = ParseUtils.parseWeatherJSON(weatherResponse);

        return weatherList;
    }

    /* prepares data about to be inserted into database */
    private static void setAnalyticsToWeatherData(List<Weather> weatherList, String localizedName, Integer persistence, Integer category) {
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
        }
    }

    /* database maintenance to be called in a runnable object as a background task */
    private static void maintainWeatherDatabase(Integer howLongVisible, Integer howLongStored) {

        /* (blocking) query the weather db for entire list */
        List<Weather> weatherList = null;
        Future<List<Weather>> getWeatherListFromDbTask = cachingExecutor.submit(new Callable<List<Weather>>() {
            @Override
            public List<Weather> call() throws Exception {
                List<Weather> weatherListNonLive = weatherDaoReference.getAllWeatherPointsNonLive();
                if (weatherListNonLive != null){
                    Log.d(TAG, "maintainWeatherDatabase: weatherListNonLive size: " +weatherListNonLive.size());
                    return weatherListNonLive;
                } else {
                    Log.d(TAG, "FATAL: maintainWeatherDatabase: querying locationDb with non-LiveData method does not return an entry");
                    return null;
                }
            }
        });
        try {
            weatherList = getWeatherListFromDbTask.get();
        } catch (Exception e) {
            Log.d(TAG, "maintainWeatherDatabase: exception; weather data not fetched. ");
            e.printStackTrace();
        }

        if (weatherList == null) {
            Log.d(TAG, "maintainWeatherDatabase: weather data not fetched; no maintenance will be done. ");
            return;
        }

        /* examine each data point's DateTime, formulate Date object, compare to current, modify age category */
        List<Weather> modifiedWeatherList = new ArrayList<>();
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            Integer elementIndex = weatherList.indexOf(weatherEntryInIter);
            Log.i(TAG, "\nElement index: " + elementIndex
//                    + "\nPoint type: (0 is 12hr, 1 is 1hr type): " + weatherEntryInIter.getCategory()
                    + "\nDateTime: " + weatherEntryInIter.getTime()
//                    + "\nID: " + weatherEntryInIter.getId()
                    + "\nPersistence: " + weatherEntryInIter.getPersistence());

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Integer ageCategory = updateDataAge(weatherEntryInIter.getTime(), howLongVisible, howLongStored);
            }
//            weatherEntryInIter.setPersistence(ageCategory);
//            modifiedWeatherList.add(elementIndex, weatherEntryInIter);
        }


        /* store the weather list back into the database by iteratively updating each data point */
//        cachingExecutor.submit(new CacheDataInDbsTask(modifiedWeatherList, weatherDaoReference, false, true));

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static Integer updateDataAge(String dateTime, Integer howLongVisible, Integer howLongStored) {
        Date currentDate = new Date(System.currentTimeMillis());
        long currentDateMillis = currentDate.getTime();

        String pattern = "yyyy-MM-dd'T'HH:mm:ssXXX";
        String date = dateTime;
        SimpleDateFormat dateFormat = null;

        /* with just this, weather data maintenance is impossible for OS below android 24, Nougat */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            dateFormat = new SimpleDateFormat(pattern);
        }
        Date d = null;
        try {
            d = dateFormat.parse(date /*your date as String*/);
        } catch (Exception e) {
            e.printStackTrace();
        }
        long parsedWeatherDataPointTimeMillis = d.getTime();

        Log.d(TAG, "Current time: "+currentDateMillis+"; parsed time of data point :" + parsedWeatherDataPointTimeMillis); // returns in millis; OK for comparison with current system time

        if (parsedWeatherDataPointTimeMillis >= currentDateMillis) {
            Log.d(TAG, "updateDataAge: this point is in the future, parsed data point > current time; remain visible.");
            return UNDER_24H;
        }

        if ( (currentDateMillis - parsedWeatherDataPointTimeMillis) >= (howLongStored*1000)) {
            Log.d(TAG, "updateDataAge: this point is older than current time by more than howLongStored "
                    +howLongVisible+"*1000, (should be over a week), and so it should not be stored at all.");
            return MORE_THAN_A_WEEK;
        }

        if ( (currentDateMillis - parsedWeatherDataPointTimeMillis) >= (howLongVisible*1000)) {
            Log.d(TAG, "updateDataAge: this point is older than current time by more than howLongVisible "
                    +howLongVisible+"*1000, (should be over a day), and so it should not be visible, but still stored.");
            return BETWEEN_DAY_AND_WEEK;
        }

        return UNDER_24H;
    }

    /* have a "type" Weather member which signifies whether
     * the data point is a 12-hour data point, an hourly data point, or an hourly data
     * point fetched from raspberry sensor (which would be the most frequent)
     * so these three different classifications of weather data points would be aggregated
     * in the same database in chronological order.
     * however, they would be routed into three separate displays, or graph trends.
     *
     * similarly, different scheduled task patterns could be grouped by different
     * locations, and if necessary differentiated by the "location" Weather member */
    /* set up scheduled tasks to be done in the background, based on cached location */

    /* space out the initial and periodic delays of each task to avoid locking of threads or
    * multiple distinct periodic tasks being run at once */
    public static synchronized void setUpPeriodicWeatherQueries(Integer tillNextQuery, Integer howLongVisible, Integer howLongStored) {

        /* set up the hourly weather query  */
        /* cancel if no response; point should instead be "no data" or just empty on graph */
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: scheduled Runnable executing hourly weather query. ");
                MonitorLocation fetchedLocation = defaultHomeLocation;
                List<Weather> hourlyWeatherList;

                /* (blocking) query the location db for relevant location */
                Future<MonitorLocation> getLocationFromDbTask = cachingExecutor.submit(new Callable<MonitorLocation>() {
                    @Override
                    public MonitorLocation call() throws Exception {
                        List<MonitorLocation> locationListNonLive = locationDaoReference.getLocationTableNonLive();
                        if (locationListNonLive != null){
                            Log.d(TAG, "setUpPeriodicWeatherQueries: locationListNonLive: "
                                    +locationListNonLive.get(0).getLocalizedName());
                        } else {
                            Log.d(TAG, "FATAL: setUpPeriodicWeatherQueries: querying locationDb with non-LiveData method does not return an entry");
                            return defaultMonitorLocationList.get(0);
                        }
                        return locationListNonLive.get(0);
                    }
                });
                try {
                    fetchedLocation = getLocationFromDbTask.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: location not fetched from database. default to "
                            + defaultMonitorLocationList.get(0).getLocalizedName());
                    e.printStackTrace();
                }

                hourlyWeatherList = getForecastFromNetwork(SINGLE_HOUR_DATA,
                        fetchedLocation, "successfully fetched single hour forecast.");

                setAnalyticsToWeatherData(hourlyWeatherList, fetchedLocation.getLocalizedName(),
                        UNDER_24H, SINGLE_HOUR_DATA);

                cachingExecutor.submit(new CacheDataInDbsTask(hourlyWeatherList.get(0), weatherDaoReference,
                        false));
            }
        }, 30, 2000, TimeUnit.SECONDS);

        /* set up the 12 hour weather query (should happen twice a day; first call can be the delayed initiation)*/

        /* set up a task (once a day) which scans the entire weather database and updates age category
        * based on age of each data point. only the last 24h should be viewable for all data point types.
         * */
        /* set up the daily weather database maintenance (do one for location too?) */
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                maintainWeatherDatabase(howLongVisible, howLongStored);

            }
        }, 50, 2000, TimeUnit.SECONDS); // period should be 60*60*24, a day


        /* set up a task (once a week) to delete weather data of type that is "too old" */
//        clearOldWeatherData();


        /* set up a task for querying the raspberry sensor at some interval */


    }


}

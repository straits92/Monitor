package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.os.Looper;
import android.util.Log;

import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.parseutils.ParseUtils;

import java.net.URL;
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
* scheduled thread pool: has method options to schedule tasks to be done in a queue like the fixed
* thread pool. tasks can be done at a delay, or at some frequency. the tasks are distributed in the
* pool based on when they should be executed.
*
* single threaded executor: size of pool is 1. remake thread killed due to exceptions. fetches and
* executes tasks submitted to queue sequentially. (n) task finishes before (n+1) taken by thread.
*
* */

public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel";
    private static final Integer SINGLE_HOUR = 1;
    private static final Integer TWELVE_HOURS = 0;

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
            Integer howLongStored = howLongVisible * 7; /* should be a week */

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
                fetchedMonitorLocation = getDefinedLocation(0, gpsLatLon, defaultMonitorLocationList);
                cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation, locationDaoReference));
            } else {
                Log.d(TAG, "getInstance: gpsLatLon is null; default to "+defaultHomeLocation.getLocalizedName());
                fetchedMonitorLocation = defaultHomeLocation;
            }

            /* weather operations */
            /* (blocking) get initial forecast (type 12h) based on initial location */
            List<Weather> initialWeatherList = getInitialForecast(fetchedMonitorLocation);

            /* (non-blocking) then cache the weather here and erase old data in case of initial forecast */
            if (initialWeatherList != null) {
                /* go through point(s) in the list and set initial analytics */
                Integer persistence = 0; // younger than 24hr
                Integer category = TWELVE_HOURS; // 12hr data point
                setAnalyticsToWeatherData(initialWeatherList, fetchedMonitorLocation.getLocalizedName(), persistence, category);
                cachingExecutor.submit(new CacheDataInDbsTask(initialWeatherList, weatherDaoReference, true));
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
                    fetchedMonitorLocation = getDefinedLocation(0, gpsLatLon, defaultMonitorLocationList); // blocking
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
    public static synchronized MonitorLocation getDefinedLocation(Integer locationType, ArrayList<String> gpsLatLon, List<MonitorLocation> defaultMonitorLocationList) {
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

    public static synchronized List<Weather> getInitialForecast(MonitorLocation location) {
        String weatherResponse;
        List<Weather> weatherList;

        /* forecastType: 0, 12hour; 1, 1hour. */
        Log.d(TAG, "getInitialForecast: building URL with location "+location.getLocation());
        URL twelveHourWeatherUrl = NetworkUtils.buildUrlForWeather(TWELVE_HOURS, location.getLocation());
        Future<String> initialWeatherTask = weatherExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(twelveHourWeatherUrl));
        try {
            weatherResponse = initialWeatherTask.get(5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.d(TAG, "getInitialForecast: timeout, initial forecast not obtained ");
            t.printStackTrace();
            return null;
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "getInitialForecast: execution or interruption exceptions");
            e.printStackTrace();
            return null;
        }
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

//            Log.i(TAG, "setAnalyticsToWeatherData: time: "+weatherEntryInIter.getTime() + " " +
//                    "Temperature: "+weatherEntryInIter.getCelsius() + " " +
//                    "Link: "+weatherEntryInIter.getLink());
        }

    }

    private static void maintainWeatherDatabase(Integer howLongVisible, Integer howLongStored) {

        // get current system time; try using routines before, after, compareTo another date,
        // to see if it is over 24 hours ago, or over a week ago; howLongVisible, howLongStored
        Date currentDate = new Date(System.currentTimeMillis());
        Log.d(TAG, "maintainWeatherDatabase: currentDate:" +currentDate.toString()+" And its current time "+System.currentTimeMillis());

        // get entire weather list from database
        List<Weather> weatherList = weatherDaoReference.getAllWeatherPointsNonLive();

        // compare present time with each point's time and modify its persistence only
        // examine each data point, extract its time, formulate Date object, compare to current

        // store the weather list back into the database
        // OR update each weather data point by mirroring each extracted point's ID. is it necessary at all?
        // wouldn't it just have the same ID since extracted from the database?


    }

    /* set up hourly weather queries based on cached location */
    public static synchronized void setUpPeriodicWeatherQueries(Integer tillNextQuery, Integer howLongVisible, Integer howLongStored) {

        /* cancel if no response; point should instead be "no data" or just empty on graph */
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: Runnable running");
                MonitorLocation fetchedLocation = defaultHomeLocation;
                String hourlyWeatherResponse;
                List<Weather> hourlyWeatherList;

                /* query the location db for relevant location in a thread spawned by runnable */
                Future<MonitorLocation> getLocationFromDbTask = locationExecutor.submit(new Callable<MonitorLocation>() {
                    @Override
                    public MonitorLocation call() throws Exception {
                        List<MonitorLocation> locationListNonLive = locationDaoReference.getLocationTableNonLive();
                        if (locationListNonLive != null){
                            Log.d(TAG, "setUpPeriodicWeatherQueries: locationListNonLive: " +locationListNonLive.get(0).getLocalizedName());
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

                /* forecastType: 0, 12hour; 1, 1hour. */
                URL hourlyWeatherUrl = NetworkUtils.buildUrlForWeather(SINGLE_HOUR, fetchedLocation.getLocation());

                /* submit weather network request with this URL */
                Future<String> hourlyWeatherTask = weatherExecutor
                        .submit((Callable<String>) new ContactWeatherApiTask(hourlyWeatherUrl));
                try {
                    hourlyWeatherResponse = hourlyWeatherTask.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: execution or interruption exceptions");
                    e.printStackTrace();
                    return;
                }
                hourlyWeatherList = ParseUtils.parseWeatherJSON(hourlyWeatherResponse);

                // set the analytics
                Integer persistence = 0; // younger than 24hr, to be updated with time
                Integer category = SINGLE_HOUR; // 1hr data point
                setAnalyticsToWeatherData(hourlyWeatherList, fetchedLocation.getLocalizedName(), persistence, category);


                /* these tasks necessitate functionality which wraps around Daos, to loop
                 * through the weather data point lists and check their age, thus extracting their
                 * DateTime data and comparing it to current system time. this must be done
                 * via RxJava functionality; the LiveData model does not allow operations on data
                 * to be done on the background thread; they are for main thread activity. */

                /* set up a task (once a day) which scans the entire weather database and updates
                * persistence category based on age of each data point. only the last 24h should
                * be viewable for all data point types. main activity redraws display when data changes
                *
                * */

                // maintainWeatherDatabase(howLongVisible, howLongStored);

                /* set up a task (once a week) to delete weather data of type that is "too old" */
//                clearOldWeatherData();

                /* have a "type" Weather member which signifies whether
                * the data point is a 12-hour data point, an hourly data point, or a data
                * point fetched from raspberry sensor (which would be the most frequent)
                * so these three different classifications of weather data points
                * would be aggregated in the same database in chronological order.
                * however, they would be routed into three separate displays, or graph trends.
                * and so this task object could have another argument passed to it; the
                * argument specifying which type of data is being received (daily, hourly, raspb)
                *
                * similarly, different scheduled task patterns could be based on different
                * locations. these different locations could be differentiated by a "type" member
                * by which they are to be identified */

                cachingExecutor.submit(new CacheDataInDbsTask(hourlyWeatherList.get(0), weatherDaoReference,
                        false));

            }
        }, 20, tillNextQuery, TimeUnit.SECONDS);


        /* set up a task for querying the raspberry sensor */



    }


}

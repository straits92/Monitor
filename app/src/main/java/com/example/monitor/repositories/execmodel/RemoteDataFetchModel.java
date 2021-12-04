package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.parseutils.ParseUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* threadpool types
*
* it seems that a threadpool itself is a thread monitoring the threads it spawns.
* fixed thread pool: threads fetch, execute tasks from a synchronized queue.
*
* cached thread pool: thread nr not fixed, no classical queue, but a synchronous queue.
* this queue can hold only one task. the pool looks for a free thread to carry out
* that task; if no free thread available, it will create a new one. the pool will also
* kill threads idle for over 60 seconds.
*
* scheduled thread pool: has method options to schedule tasks to be done in a queue
* like the fixed thread pool. the tasks can be done at a delay, or at some frequency.
* the tasks are distributed in the pool based on when they should be executed.
*
* single threaded executor: size of pool is 1. recreates threads killed due to
* exceptions. fetches and executes tasks submitted to queue sequentially. first task submitted
* finishes before the second one is taken by the thread.
*
* */


public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel: ";
    private static RemoteDataFetchModel instance;
    private static WeatherDao weatherDaoReference;
    private static LocationDao locationDaoReference;
    private static Application applicationFromRepository;
    private static MonitorLocation defaultHomeLocation;
    private static List<MonitorLocation> defaultMonitorLocationList;

    /* a location single executor to do any prompted location data fetch,
    * a weather single executor to do the 12hour weather data fetches,
    * a caching single executor for storing data into databases via Dao,
    * a 2-thread pool for the scheduled hourly weather fetches (for API and raspberry).
    */
    private static ExecutorService weatherExecutor;
    private static ExecutorService locationExecutor;
    private static ExecutorService cachingExecutor;
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
            Integer tillNextQuery = 60 * 60; // a minute
            Integer howLongVisible = 60 * 60 * 24; // a day
            Integer howLongStored = howLongVisible * 7; // a week

            /* initiate all necessary executors
            * maybe have executors based on what they do; database executors, and network executors */
            weatherExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // weather network
            locationExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // location network, gps
            cachingExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // all caching into db
            scheduledExecutor = ExecutorHelper.getScheduledPool(); /* specify nr of threads? */

            // ----- startup tasks

            /* Design note: attempting to have multiple locations in the location database, and
             * to update them instead of delete / insert, relies on either knowing the exact
             * primary key of the location entry to be updated, or the current value of the entry.
             * since observe() should only be called at the activity level, observeForever() was used
             * here, and the result was that even though a locationdb change was detected, the actual
             * location list was still fetched as null. thus, the decision is to keep only one
             * location entry at all times, and to tie to each Weather data point the location
             * for which it was obtained.*/

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

            /* (blocking) get GPS lat,lon from android. then, get location key from api. */
            MonitorLocation fetchedLocation = getCurrentLocation(0);

            /* (non-blocking) add home location in database after default is set up; clears the location database */
            cachingExecutor.submit(new CacheDataInDbsTask(fetchedLocation, locationDaoReference));

            /* weather operations */
            /* (blocking) get initial forecast (type 12h) based on initial location */
            List<Weather> initialWeatherList = getInitialForecast(fetchedLocation);

            /* then cache the weather here and erase old data in case of initial forecast; non-blocking */
            cachingExecutor.submit(new CacheDataInDbsTask(initialWeatherList, weatherDaoReference, true));

            // ----- end startup tasks


            // ----- periodic tasks: require RxJava to operate on data in the background

            /* periodic request. pass: request frequency, data aging and visibility, deletion policy */
            setUpPeriodicWeatherQueries(tillNextQuery, howLongVisible, howLongStored); /* intervals in seconds */

            // ----- end periodic tasks
        }
        return instance;
    }

    
    /* (blocking) wrapper method invoking gps task and accuweather location task */
    /* returns: location obtained via GPS, with accuweather code. Otherwise,
    * returns default location specified by locationType argument */
    public static MonitorLocation getCurrentLocation(Integer locationType) {
        String locationResponse;
        URL locationUrl;
        MonitorLocation fetchedMonitorLocation;
        boolean isFromGps = false;
        ArrayList<String> gpsLatLon; // uninitiated

        /* may need to create default location object in case of invocation by higher components,
        * or pass such an object to the function from the caller context */

        /* Android GPS blocking 2500ms for home location */
        Future<ArrayList<String>> gpsLatLonTask = locationExecutor.submit(new GetGpsTask(applicationFromRepository));
        try {
            gpsLatLon = gpsLatLonTask.get(2500, TimeUnit.MILLISECONDS);
            if (gpsLatLon == null)
                return defaultMonitorLocationList.get(locationType);            
            
            isFromGps = true; /* set at successful return from gps task */
        } catch (TimeoutException t) {
            Log.i(TAG, "getCurrentLocation() at GPS task: TimeoutException; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
            return defaultMonitorLocationList.get(locationType);
        } catch (ExecutionException | InterruptedException ei) {
            Log.i(TAG, "getCurrentLocation() at GPS task: other Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
//            ei.printStackTrace();
            return defaultMonitorLocationList.get(locationType);
        }

        /* Accuweather API query */
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = locationExecutor.submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
        try {
            locationResponse = initialLocationTask.get(4500, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: obtained location response from Accuweather");

            /* parse locationResponse to get the actual location key */
            fetchedMonitorLocation = ParseUtils.parseLocationJSON(locationResponse);
            fetchedMonitorLocation.setGpsAvailable(isFromGps); // if fetched by gps
            fetchedMonitorLocation.setLocationType(locationType);

        } catch (TimeoutException t) {
//            t.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Timeout; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
            return defaultMonitorLocationList.get(locationType);
        } catch (ExecutionException | InterruptedException ei) {
//            ei.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: other Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
            return defaultMonitorLocationList.get(locationType);
        }

        /* return location object for further use by weather url */
        return fetchedMonitorLocation;
    }


    public static synchronized List<Weather> getInitialForecast(MonitorLocation location) {
        String weatherResponse;
        List<Weather> weatherList;

        /* forecastType: 0, 12hour; 1, 1hour. */
        URL twelveHourWeatherUrl = NetworkUtils.buildUrlForWeather(0, location.getLocation());
        Future<String> initialWeatherTask = weatherExecutor
                .submit((Callable<String>) new ContactWeatherApiTask(twelveHourWeatherUrl));
        try {
            weatherResponse = initialWeatherTask.get(2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.d(TAG, "getInitialForecast: timeout, initial forecast not obtained ");
//            t.printStackTrace();
            return null;
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "getInitialForecast: execution or interruption exceptions");
            e.printStackTrace();
            return null;
        }

        /* parse the JSON response into the List structure needed for the database */
        weatherList = ParseUtils.parseWeatherJSON(weatherResponse);

        /* go through point(s) in the list and set initial analytics */
        Integer persistence = 0; // younger than 24hr
        Integer category = 0; // 12hr data point
        setAnalyticsToWeatherData(weatherList, location.getLocalizedName(), persistence, category);

        return weatherList;
    }

    /* prepares data about to be inserted into database */
    private static void setAnalyticsToWeatherData(List<Weather> weatherList, String localizedName, Integer persistence, Integer category) {
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            weatherEntryInIter.setLocation(localizedName);
            weatherEntryInIter.setPersistence(persistence);
            weatherEntryInIter.setCategory(category);
//            Log.i(TAG, "setAnalyticsToWeatherData: time: "+weatherEntryInIter.getTime() + " " +
//                    "Temperature: "+weatherEntryInIter.getCelsius() + " " +
//                    "Link: "+weatherEntryInIter.getLink());
        }

    }

    private static void maintainWeatherDatabase(Integer howLongVisible, Integer howLongStored) {

        // get current system time

        // compare present time with each point's time

        // update its persistence based on the comparison

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
                Future<MonitorLocation> task = locationExecutor.submit(new Callable<MonitorLocation>() {
                    @Override
                    public MonitorLocation call() throws Exception {
                        Log.d(TAG, "query location database for cached location");

                        // can this obtain the location from the database, or is some live data observer needed?
                        // apparently if "block until there is a value" is needed, RxJava should be used, to observe bg thread
                        List<MonitorLocation> locationList = locationDaoReference.getLocationTable().getValue();

                        if (locationList == null){
                            Log.d(TAG, "FATAL: setUpPeriodicWeatherQueries: querying locationDb returns null.");
                        }
                        MonitorLocation monitorLocation = locationList.get(0);
                        return monitorLocation;

                    }
                });

                // block until location from database is fetched
                try {
                    fetchedLocation = task.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: run: location not fetched from database. default to "
                            + defaultMonitorLocationList.get(0).getLocalizedName());
                    e.printStackTrace();
                }

                /* forecastType: 0, 12hour; 1, 1hour. */
                URL hourlyWeatherUrl = NetworkUtils.buildUrlForWeather(1, fetchedLocation.getLocation());

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

                // parse the eventual response from JSON objects to a List structure
                hourlyWeatherList = ParseUtils.parseWeatherJSON(hourlyWeatherResponse);

                // set the analytics
                Integer persistence = 0; // younger than 24hr, to be updated with time
                Integer category = 1; // 1hr data point
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

    /* intended to be called on main thread, in activity? */
    public static List<MonitorLocation> getLocationValue(LiveData<List<MonitorLocation> > liveData) throws InterruptedException {
        final Object[] objects = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);

        Observer observer = new Observer() {
            @Override
            public void onChanged(@Nullable Object o) {
                objects[0] = o;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        liveData.observeForever(observer);
        latch.await(2, TimeUnit.SECONDS);
        return (List<MonitorLocation>) objects[0];
    }



    /* provide method that exposes GPS functionality to parent architecture components,
    * such that a button can be set up for the user to request a location update. there
    * could be multiple buttons for different "types" of location; a home location button
    * would specifically introduce a location entry with a "type" of home */

}

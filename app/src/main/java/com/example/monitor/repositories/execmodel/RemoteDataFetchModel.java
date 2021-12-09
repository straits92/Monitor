package com.example.monitor.repositories.execmodel;

import android.app.Application;
import android.os.Looper;
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
import java.util.Calendar;
import java.util.Date;
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
    private static ExecutorService serviceExecutor;
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
            Integer tillNextQuery = 40; // a minute
            Integer howLongVisible = 60 * 60 * 24; // a day
            Integer howLongStored = howLongVisible * 7; // a week

            /* initiate all necessary executors
            * maybe have executors based on what they do; database executors, and network executors */
            weatherExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // weather network
            locationExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // location network, gps
            cachingExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // all caching into db
            serviceExecutor = ExecutorHelper.getSingleThreadExecutorInstance(); // for user purposes
            scheduledExecutor = ExecutorHelper.getScheduledPool(); /* specify nr of threads? */

            // ----- startup tasks

            /* location operations */
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
            MonitorLocation fetchedLocation = getCurrentLocation(0, defaultMonitorLocationList);

            /* (non-blocking) add home location in database after default is set up */
            cachingExecutor.submit(new CacheDataInDbsTask(fetchedLocation, locationDaoReference));

//            ArrayList<String> gpsLatLon = getGpsLatLon();
//            MonitorLocation fetchedMonitorLocation = null;
//            if (gpsLatLon != null) {
//                fetchedMonitorLocation = getDefinedLocation(0, gpsLatLon, defaultMonitorLocationList);
//                cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation, locationDaoReference));
//            }



            /* weather operations */
            /* (blocking) get initial forecast (type 12h) based on initial location */
            List<Weather> initialWeatherList = getInitialForecast(fetchedLocation);

            /* (non-blocking) then cache the weather here and erase old data in case of initial forecast */
            if (initialWeatherList != null) {
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
//        serviceExecutor.submit(new Runnable() {
//            @Override
//            public void run() {


                /* (blocking) get GPS lat,lon from android. then, get location key from api. */
                MonitorLocation fetchedLocation = getCurrentLocation(0, defaultMonitorLocationList);

                /* (non-blocking) add home location in database after default is set up; clears the location database */
                cachingExecutor.submit(new CacheDataInDbsTask(fetchedLocation, locationDaoReference));

//                ArrayList<String> gpsLatLon = getGpsLatLon();
//                MonitorLocation fetchedMonitorLocation = null;
//                if (gpsLatLon != null) {
//                    fetchedMonitorLocation = getDefinedLocation(0, gpsLatLon, defaultMonitorLocationList);
//                    cachingExecutor.submit(new CacheDataInDbsTask(fetchedMonitorLocation, locationDaoReference));
//                }


//            }
//        });
    }

    /* decoupling getCurrentLocation; GPS task */
    public static synchronized ArrayList<String> getGpsLatLon() {
        ArrayList<String> gpsLatLon = null;
        Future<ArrayList<String>> gpsLatLonTask = locationExecutor.submit(new GetGpsTask(applicationFromRepository));
        try {
            Log.d(TAG, "getCurrentLocation: if this is causing a block, 'PASSED' will not appear");
//            gpsLatLon = gpsLatLonTask.get(); // blocking
            gpsLatLon = gpsLatLonTask.get(5000, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: PASSED");
        } catch (TimeoutException t) {
            Log.i(TAG, "getCurrentLocation() at GPS task: TimeoutException");
            t.printStackTrace();
        } catch (ExecutionException | InterruptedException ei) {
            Log.i(TAG, "getCurrentLocation() at GPS task: other Exception");
            ei.printStackTrace();
        }

        return gpsLatLon;
    }

    /* decoupling getCurrentLocation; accuweather task */
    public static synchronized MonitorLocation getDefinedLocation(Integer locationType, ArrayList<String> gpsLatLon, List<MonitorLocation> defaultMonitorLocationList) {
        String locationResponse;
        URL locationUrl;
        MonitorLocation fetchedMonitorLocation = defaultMonitorLocationList.get(locationType);

        /* Accuweather API query to get actual location key */
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = locationExecutor.submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
        try {
            locationResponse = initialLocationTask.get(4500, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: obtained location response from Accuweather");
            fetchedMonitorLocation = ParseUtils.parseLocationJSON(locationResponse);
            fetchedMonitorLocation.setGpsAvailable(true); // if fetched by gps
            fetchedMonitorLocation.setLocationType(locationType);

        } catch (TimeoutException t) {
//            t.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Timeout; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
//            return defaultMonitorLocationList.get(locationType);
        } catch (ExecutionException | InterruptedException ei) {
            ei.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: other Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
//            return defaultMonitorLocationList.get(locationType);
        }

        return fetchedMonitorLocation;
    }


    /* (blocking) wrapper method invoking gps task and accuweather location task */
    /* returns: location obtained via GPS, with accuweather code. Otherwise,
    * returns default location specified by locationType argument */
    public static synchronized MonitorLocation getCurrentLocation(Integer locationType, List<MonitorLocation> defaultMonitorLocationList) {
        String locationResponse;
        URL locationUrl;
        MonitorLocation fetchedMonitorLocation = defaultMonitorLocationList.get(locationType);
        ArrayList<String> gpsLatLon;

        /* Issue:
        GPS blocking seems to delay the lat,lon return till after timeout when submitted as Runnable.

        When submitted as Runnable without a specified timeout, it blocks indefinitely.

        When done on main thread (no Runnable), it completes without issue.


        So when a gps task is submitted to the locationExecutor, it is as if this worker thread
        cannot proceed; times out; the flow of the serviceExecutor continues with getCurrentLocation
        and the worker thread actually emits a side effect of printing out the correct lat,lon.
        Is it the case that access to the locationDb blocks further execution?

        as if the act of get() on the caller (nonmain) thread blocks the activity on the other worker
        thread. And GPS needs the application context passed to it. Does it force the GPS functionality
        to be done on the main thread?
        */
        Future<ArrayList<String>> gpsLatLonTask = locationExecutor.submit(new GetGpsTask(applicationFromRepository));
        try {

            Log.d(TAG, "getCurrentLocation: if this is causing a block, 'PASSED' will not appear");
//            gpsLatLon = gpsLatLonTask.get(); // blocking
            gpsLatLon = gpsLatLonTask.get(3000, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: PASSED");
            if (gpsLatLon == null) {
                return defaultMonitorLocationList.get(locationType);
            }
            
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

        Log.d(TAG, "getCurrentLocation: after gps task; lat: "+gpsLatLon.get(0)+" lon:"+gpsLatLon.get(1));

        /* Accuweather API query to get actual location key */
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = locationExecutor.submit((Callable<String>) new ContactWeatherApiTask(locationUrl));
        try {
            locationResponse = initialLocationTask.get(4500, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: obtained location response from Accuweather");
            fetchedMonitorLocation = ParseUtils.parseLocationJSON(locationResponse);
            fetchedMonitorLocation.setGpsAvailable(true); // if fetched by gps
            fetchedMonitorLocation.setLocationType(locationType);

        } catch (TimeoutException t) {
//            t.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: Timeout; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
//            return defaultMonitorLocationList.get(locationType);
        } catch (ExecutionException | InterruptedException ei) {
//            ei.printStackTrace();
            Log.d(TAG, "getCurrentLocation at accuweather query: other Exception; default location: "
                    + defaultMonitorLocationList.get(locationType).getLocalizedName());
//            return defaultMonitorLocationList.get(locationType);
        }

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
            weatherResponse = initialWeatherTask.get(5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.d(TAG, "getInitialForecast: timeout, initial forecast not obtained ");
//            t.printStackTrace();
            return null;
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "getInitialForecast: execution or interruption exceptions");
            e.printStackTrace();
            return null;
        }
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
                Future<MonitorLocation> task = locationExecutor.submit(new Callable<MonitorLocation>() {
                    @Override
                    public MonitorLocation call() throws Exception {
                        Log.d(TAG, "setUpPeriodicWeatherQueries: query location database for cached location");

                        // apparently if "block until there is a value" is needed, RxJava should be used, to observe bg thread
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

                // block until location from database is fetched
                try {
                    fetchedLocation = task.get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.d(TAG, "setUpPeriodicWeatherQueries: location not fetched from database. default to "
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



        /*  for stackoverflow */
        // schedule an hourly task
//        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//
//                // other tasks...
//
//                // get data from database via Dao
//                Future<List<Weather>> task = weatherExecutor.submit(new Callable<List<Weather>>() {
//                    @Override
//                    public List<Weather> call() throws Exception {
//                        List<Weather> weatherList = weatherDaoReference.getAllWeatherPoints().getValue();
//                        if (weatherList == null){
//                            Log.d(TAG, "FATAL: no data fetched for processing");
//                        }
//                        return weatherList;
//                    }
//                });
//
//                // block until location from database is fetched
//                try {
//                    fetchedWeatherList = task.get();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//                // do some data processing with the List<Weather> ...
//
//            }
//        }, initialDelay, tillNextQuery, TimeUnit.SECONDS);

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



}

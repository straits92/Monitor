package com.example.monitor.repositories.execmodel;

import android.app.Application;
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
import java.util.List;
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

/* So some overall scheduler could run in the background and issue network calls
 * for accuweather every hour. but this call itself should be cancelled if the
 * response is taking more than a few seconds, and skipped? then both the skipped
 * and the next hour could be tried in the future, to compensate?
 * Run weather requests hourly, and for a 12hr period, based on STORED location.
 * Run location requests at startup, and at user prompt only, at first.
 */
public class RemoteDataFetchModel {
    private static final String TAG = "RemoteDataFetchModel: ";
    private static RemoteDataFetchModel instance;
    private static WeatherDao weatherDaoReference;
    private static LocationDao locationDaoReference;
    private static Application applicationFromRepository;

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
            instance = new RemoteDataFetchModel();
            weatherDaoReference = weatherDao;
            locationDaoReference = locationDao;
            applicationFromRepository = application;

            /* initiate all necessary executors */
            weatherExecutor = ExecutorHelper.getSingleThreadExecutorInstance();
            locationExecutor = ExecutorHelper.getSingleThreadExecutorInstance();
            cachingExecutor = ExecutorHelper.getSingleThreadExecutorInstance();
            scheduledExecutor = ExecutorHelper.getScheduledPool();

            /* get GPS lat,lon from android. then, get location key from accuweather based on that.
            * at app startup this will be a blocking task, performed in another thread while the
            * main thread waits. but onClick and periodic, this task will be in the background.
            * then write the location key, and the lat/lon, to the location db.
            * there should also be a methodology to be followed regarding weather data stored
            * in case of a location change. */
            String location = getCurrentLocation(); /* blocking: gets the location key to be part of weatherURL */

            /* get forecast based on initial location (blocking) */
            getInitialForecast(location);

            /* periodic hourly request based on cached location. use seconds, minutes for testing */
            setUpPeriodicWeatherQueries((Integer) 30);

        }
        return instance;
    }

    /* wrapper method invoking gps task and accuweather location task*/
    public static String getCurrentLocation() {
        String locationResponse = null;
        URL locationUrl;
        boolean isFromGps = false;

        /* default is Novi Sad. Belgrade data: location "298198", lat: 44.8125, lon: 20.4612  */
        ArrayList<String> gpsLatLon = new ArrayList<>();
        gpsLatLon.add(0, " 45.267136"); gpsLatLon.add(1, "19.833549");
        List<MonitorLocation> monitorLocationList = new ArrayList<>();
        monitorLocationList.add(new MonitorLocation("298486", "Novi Sad", gpsLatLon.get(0), gpsLatLon.get(1), false));

        /* Android GPS blocking task */
        Future<ArrayList<String>> gpsLatLonTask = locationExecutor.submit(new getGpsTask(applicationFromRepository, gpsLatLon));
        try {
            gpsLatLon = gpsLatLonTask.get(2500, TimeUnit.MILLISECONDS);
            isFromGps = true;
        } catch (TimeoutException t) {
//            t.printStackTrace();
            Log.i(TAG, "getCurrentLocation(): TimeoutException; default location: "+monitorLocationList.get(0).getLocalizedName());
        } catch (ExecutionException | InterruptedException ei) {
            Log.i(TAG, "getCurrentLocation(): Execution or Interrupted exception");
            ei.printStackTrace();
        }

        /* Accuweather API query */
        locationUrl = NetworkUtils.buildUrlForLocation(gpsLatLon.get(0), gpsLatLon.get(1));
        Future<String> initialLocationTask = locationExecutor.submit(new contactWeatherApiTask(locationUrl));
        try {
            locationResponse = initialLocationTask.get(4500, TimeUnit.MILLISECONDS);
            Log.d(TAG, "getCurrentLocation: obtained location response from Accuweather");

            /* parse locationResponse to get the actual location key */
            monitorLocationList = ParseUtils.parseLocationJSON(locationResponse);
            monitorLocationList.get(0).setGpsAvailable(isFromGps); // if fetched by gps
        } catch (TimeoutException t) {
//            t.printStackTrace();
            Log.d(TAG, "getCurrentLocation: timeout of accuweather API query; default location: "+monitorLocationList.get(0).getLocalizedName());
        } catch (ExecutionException | InterruptedException ei){
            ei.printStackTrace();
        }

        /* cache location data into LiveData via Dao on worker thread; non-blocking */
        cachingExecutor.submit(new cachingLocationOrWeatherDataTask(monitorLocationList, locationDaoReference));

        /* return location key info for further use by weather url */
        return monitorLocationList.get(0).getLocation();
    }

    public static synchronized void getInitialForecast(String location) {
        String weatherResponse;
        List<Weather> weatherList;

        /* forecastType: 0, 12hour; 1, 1hour. */
        URL twelveHourWeatherUrl = NetworkUtils.buildUrlForWeather(0, location);
        Future<String> initialWeatherTask = weatherExecutor
                .submit(new contactWeatherApiTask(twelveHourWeatherUrl));
        try {
            weatherResponse = initialWeatherTask.get(2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException t) {
            Log.d(TAG, "getInitialForecast: timeout, initial forecast not obtained ");
//            t.printStackTrace();
            return;
        } catch (ExecutionException | InterruptedException e) {
            Log.d(TAG, "getInitialForecast: execution or interruption exceptions");
            e.printStackTrace();
            return;
        }

        /* parse the JSON response into the List structure needed for the database */
        weatherList = ParseUtils.parseWeatherJSON(weatherResponse);

        /* then cache the weather here; non-blocking */
        cachingExecutor.submit(new cachingLocationOrWeatherDataTask(weatherList, weatherDaoReference));

    }

    public static synchronized void setUpPeriodicWeatherQueries(Integer seconds) {

        /* define the structure of the hourly weather query tasks */



        /* URl to be built each time the hourly query to be done based on cached loc */
//        URL hourlyWeatherUrl = NetworkUtils.buildUrlForWeather(1, location);

        /* periodically scheduled tasks should be runnable, not callable; only schedule()
        * runs a callable? */
//        Future<Void> periodicWeatherTask = scheduledExecutor
//                .scheduleAtFixedRate(new contactWeatherApiTask(hourlyWeatherUrl), 0, 1, TimeUnit.HOURS);


    }

    /* provide method that exposes GPS functionality to parent architecture components,
    * such that a button can be set up for the user to request a location update */

}

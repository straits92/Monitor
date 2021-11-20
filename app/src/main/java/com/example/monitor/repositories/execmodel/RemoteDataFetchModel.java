package com.example.monitor.repositories.execmodel;

import android.util.Log;

import androidx.room.Dao;

import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.Location;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.parseutils.ParseUtils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
* exceptions. fetches and executes tasks submitted to queue sequentially.
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
    /* always runs first task submitted before the second one. so if the first finishes
    * before the second gets taken up by the thread, safe to submit a http location request,
    * followed by a http weather request? */
    /* so want a single executor to do the initial loc+weather data fetch,
    * and the 12hour fetches,
    * but a thread pool for the scheduled hourly weather fetches.
    * and maybe another single executor for subsequent Dao operations; whose tasks
    * will actually be submitted by a thread from the scheduled hourly fetches? */
    private static ExecutorService singleExecutor;
    private static ScheduledExecutorService scheduledExecutor;
    /* if Dao access can't be made via reference, then instead of a static instance, it may be
    * necessary to construct the RemoteDataFetchModel via constructor and assign a non-static
    * Dao */

    /* default location data; should it be here or in another module? */
    private static String location = "298198";
    private static String localizedName = "Belgrade";

    /* data should be obtained from GPS task thread; reference values are:
    * Novi Sad: lat: 44.818, lon: 20.457
    * Belgrade: lat: 44.8125, lon: 20.4612  */
    private static boolean GPS_AVAILABLE = false;
    private static AtomicReference<String> latitude = new AtomicReference<>("44.8125");
    private static AtomicReference<String> longitude = new AtomicReference<>("20.4612");

    /* singleton, instantiated in environment which provides a data access object */
    public static RemoteDataFetchModel getInstance(WeatherDao weatherDao, LocationDao locationDao) {
        if (instance == null){
            instance = new RemoteDataFetchModel();
            weatherDaoReference = weatherDao;
            locationDaoReference = locationDao;

            /* initiate all necessary executors */
            singleExecutor = ExecutorHelper.getSingleThreadExecutorInstance();
            scheduledExecutor = ExecutorHelper.getScheduledPool();

            /* fetching GPS data to be done in separate thread for lat, lon info, prior to
            * forming the URL for a location request. If failed, default to Belgrade lat,lon */

            /* prepare URLs before passing them into tasks */
            /* forecastType: 0, 12hour; 1, 1hour. */
            /* periodic locationUrl should fetch location data from locationDao; not implemented */
            URL locationUrl = NetworkUtils.buildUrlForLocation(latitude.get(), longitude.get());
            URL twelveHourWeatherUrl = NetworkUtils.buildUrlForWeather(0, location);
            URL hourlyWeatherUrl = NetworkUtils.buildUrlForWeather(1, location);

            /* submit initial data task, and periodic tasks, to respective executors */
            Future<String> initialLocationTask = singleExecutor
                    .submit(new contactWeatherApiTask(locationUrl));
            Future<String> initialWeatherTask = singleExecutor
                    .submit(new contactWeatherApiTask(twelveHourWeatherUrl));

            /* but where should this be tried and caught? */
//            Future<Void> periodicWeatherTask = scheduledExecutor
//                    .scheduleAtFixedRate(new contactWeatherApiTask(hourlyWeatherUrl), 0, 1, TimeUnit.HOURS);

            /* Separate thread blocks until the response is obtained in Future<>. Extract network
            * response by parseJSON methods. Then update LiveData via Dao. Use another executor? */
            initialReceiveResponsesAndCacheData(initialLocationTask, initialWeatherTask);

        }
        return instance;
    }

    /* receive+cache location then weather info. potentially decouple. */
    private static synchronized Void initialReceiveResponsesAndCacheData(Future<String> locationResponse, Future<String> weatherResponse)
            /*throws ExecutionException, InterruptedException*/ {
        /* this executor, or another one decoupled from weatherAPI tasks?? */
        Future<Void> task = singleExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                List<Weather> weatherList;
                List<Location> locationList;
                String successfulLocation;
                String successfulWeather;

                /* blocking methods. if either fail, parsing + caching via Dao do not occur */
                try {
                    successfulLocation = locationResponse.get();
                } catch (ExecutionException | InterruptedException e) {
                    successfulLocation ="{\"Version\":1,\"Key\":\"298198\",\"Type\":\"City\",\"Rank\":20,\"LocalizedName\":\"Belgrade\",\"EnglishName\":\"Belgrade\",\"PrimaryPostalCode\":\"\"}";
                    e.printStackTrace();
                    return null;
                }

                try {
                    successfulWeather = weatherResponse.get();
                } catch (ExecutionException | InterruptedException e) {
                    successfulWeather = "{}";
                    e.printStackTrace();
                    return null;
                }

                /* parseJSON methods to get ArrayList<Weather> and ArrayList<Location> */
                weatherList = ParseUtils.parseWeatherJSON(successfulWeather);
                locationList = ParseUtils.parseLocationJSON(successfulLocation);

                /* ArrayList implements abstract List, so it should be possible to pass it
                 as such to a Dao method without explicitly extracting entries
                 from ArrayList before calling the Dao method?? or extract from ArrayList? */
                /* delete + insert LiveData could instead be updated, or just called from another
                * synchronized method, leaving this caller unsynced */
                weatherDaoReference.deleteAllWeatherPoints();
                locationDaoReference.deleteLocationTable();
                /* then insert new data into LiveData via Dao */
                weatherDaoReference.insertWeatherList(weatherList);
                locationDaoReference.insertLocationList(locationList);

                return null;
            }
        });
        /* don't "block until completion" */
        return null /*task.get()*/;
    }




}

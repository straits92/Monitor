package com.example.monitor.repositories;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.LocationDatabase;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.databases.WeatherDatabase;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.repositories.execmodel.RemoteDataFetchModel;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/* single source of truth for weather data */
/* The repository should decide when to fetch data from which source. The fetching of data is NOT
nominally done at the user's prompt. It is intended as a periodic background task. It should not
primarily be triggered by the MainActivity, but such functionality may be wrapped for it. As such,
the ViewModel does not govern data fetching execution logic in the background. */
public class WeatherRepository {
    private static final String TAG = "WeatherRepository: ";
    private WeatherDao weatherDao;
    private LocationDao locationDao;
    private LiveData<List<Weather>> weatherDataEntries;
    private LiveData<List<MonitorLocation>> locationData;

    /* has its own executor for single datapoint insertions, but should have
    * a method which inserts an entire list of data points at once too, in one thread */
    private static ExecutorService singleExecutor;

    /* for fetching remote data via managed, scheduled execution */
    private RemoteDataFetchModel remoteModel;

    /* repository is aware if something is updating */
    private MutableLiveData<Boolean> isUpdating = new MutableLiveData<>();

    /* potential singleton alternative: return static instance via getInstance(application) */
    /* singleton pattern used to avoid having open connections to web servers, APIs, caches etc */
    public WeatherRepository(Application application) { /* application is a subclass of context */
        Log.d(TAG, "Constructor: repository to be instantiated!");
        WeatherDatabase weatherDatabase  = WeatherDatabase.getInstance(application);
        LocationDatabase locationDatabase = LocationDatabase.getInstance(application);
        weatherDao = weatherDatabase.weatherDao();
        locationDao = locationDatabase.locationDao();
        weatherDataEntries = weatherDao.getAllWeatherPoints();
        locationData = locationDao.getLocationTable();

        isUpdating.setValue(false); /* initial value for weather list */
        singleExecutor = ExecutorHelper.getSingleThreadExecutorInstance();

        /* Instantiate background execution model. Wrap its methods for use by ViewModel and thus
        * the MainActivity in case the user needs to trigger remote data fetching. It needs
        * a reference to application in order to invoke GPS tasks */
        remoteModel = RemoteDataFetchModel.getInstance(weatherDao, locationDao, application);

    }

    /* Room sets up this database operation to run on a bg thread, according to CodingInFlow. */
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return weatherDataEntries;
    }

    /* Room should also set this up? To be used in an observer, maybe by adapter? */
    public LiveData<List<MonitorLocation>> getLocationData() {return locationData;}

    /* for the buffering object in MainActivity */
    public MutableLiveData<Boolean> getIsUpdating() {
        return isUpdating;
    }

    /* wrapper interface for remote data, from remoteModel; both weather API and the Pi-sensor */
    public void requestDataFromWeatherApi() {
        // operate on some live data
    }

    public void requestDataFromSensors() {
        // operate on some live data
    }

    public void updateLocationOnPrompt() {
        remoteModel.updateLocationOnPrompt();
    }


    /* METHODS USED IN VIEWMODEL */

    /* Database operation wrappers calling Dao methods in bg threads.
    * Thread tasks should be private static classes, so that they don't reference the repository. */
    public void insert(Weather weatherDataPoint) {
        try {
            this.insertWeatherDataPoint(weatherDataPoint);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* DUMMY CODE: invoked in MainActivity via Fab button; works here but not in the abstract database */
    public synchronized Void insertWeatherDataPoint(final Weather weatherEntity)
            throws ExecutionException, InterruptedException {

        /* executor.submit(task) returns a placeholder variable of type Future<> */
        Future<Void> task = singleExecutor.submit(new Callable<Void>() {

            /* executor runs this callable */
            @Override
            public Void call() {
                Log.d(TAG, "call: insertWeatherDataPoint: inserted");
                weatherDao.insert(weatherEntity);
                /* a deliberate delay needed here to see the progress buffer */
                isUpdating.postValue(false);
                return null;
            }
        });

        /* blocking operation waits for the task to complete; not needed with live data? */
        return task.get();
    }







    /* take in a list, or just an ArrayList of points and iterate? */
//    public void insertList(List<Weather> weatherDataPoints){
//        try {
//            this.insertWeatherList(weatherDataPoints);
//        } catch (ExecutionException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }


    /* entirely unnecessary for the ViewModel? */
//    public synchronized Void insertWeatherList(List<Weather> weatherList)
//            throws ExecutionException, InterruptedException {
//        Future<Void> task = singleExecutor.submit(new Callable<Void>() {
//            @Override
//            public Void call() {
//                Log.d(TAG, "call: insertWeatherList: inserted");
//                weatherDao.insertWeatherList(weatherList);
////                isUpdating.postValue(false);
//                return null;
//            }
//        });
//        return task.get();
//    }

}

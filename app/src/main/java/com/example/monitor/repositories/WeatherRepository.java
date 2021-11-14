package com.example.monitor.repositories;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.databases.WeatherDao;
import com.example.monitor.databases.WeatherDatabase;
import com.example.monitor.models.Weather;
import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.repositories.execmodel.RemoteDataFetchModel;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/* The repository should decide when to fetch data from which source. The fetching of data is NOT
nominally done at the user's prompt. It is intended as a periodic background task. It should not
primarily be triggered by the MainActivity, but such functionality may be wrapped for it. As such,
the ViewModel does not govern data fetching execution logic in the background. */
public class WeatherRepository {
    private static final String TAG = "WeatherRepository: ";
    private WeatherDao weatherDao;
    private LiveData<List<Weather>> weatherDataEntries;

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
        Log.d(TAG, "WeatherRepository: repository to be instantiated!");
        WeatherDatabase database  = WeatherDatabase.getInstance(application);
        weatherDao = database.weatherDao();
        weatherDataEntries = weatherDao.getAllWeatherPoints();
        isUpdating.setValue(false); /* initial value */
        singleExecutor = ExecutorHelper.getSingleThreadExecutorInstance();
        Log.d(TAG, "WeatherRepository: dummy data points inserted during database instantiation!");

        /* The repository should instantiate an execution model, and then use its functionalities
        * to provide a wrapping interface which will be offered to the ViewModel and thus the
        * MainActivity in case the user needs to trigger some remote action */
        remoteModel = RemoteDataFetchModel.getInstance();
    }

    /* Room sets up this database operation to run on a bg thread, according to CodingInFlow. */
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return weatherDataEntries;
    }

    /* for the buffering object in MainActivity */
    public MutableLiveData<Boolean> getIsUpdating() {
        return isUpdating;
    }

    /* wrapper interface for remote data, from remoteModel; both weather API and the Pi-sensor */
    public void requestDataFromWeatherApi() {
    }

    public void requestDataFromSensors() {
    }

    /* Database operation wrappers calling Dao methods in bg threads, used by the ViewModel.
    * Thread tasks should be private static classes, so that they don't reference the repository. */
    public void insert(Weather weatherDataPoint) {
        try {
            this.insertWeatherDataPoint(weatherDataPoint);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* take in a list, or just an ArrayList of points and iterate? */
    public void insertList(List<Weather> weatherDataPoints){
        try {
            this.insertWeatherList(weatherDataPoints);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* boilerplate callables wrapping Dao method calls, to be submitted to executor  */

    /* currently invoked in MainActivity via Fab button; works here but not in the abstract database */
    public synchronized Void insertWeatherDataPoint(final Weather weatherEntity)
            throws ExecutionException, InterruptedException {
        Future<Void> task = singleExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                Log.d(TAG, "call: insertWeatherDataPoint: inserted");
                weatherDao.insert(weatherEntity);
                /* a deliberate delay needed here to see the progress buffer */
                isUpdating.postValue(false);
                return null;
            }
        });
        return task.get();
    }

    public synchronized Void insertWeatherList(List<Weather> weatherList)
            throws ExecutionException, InterruptedException {
        Future<Void> task = singleExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                Log.d(TAG, "call: insertWeatherList: inserted");
                weatherDao.insertWeatherList(weatherList);
//                isUpdating.postValue(false);
                return null;
            }
        });
        return task.get();
    }

}

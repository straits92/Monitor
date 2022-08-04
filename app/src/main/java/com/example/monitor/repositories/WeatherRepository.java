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
public class WeatherRepository {
    private static final String TAG = "WeatherRepository: ";
    private WeatherDao weatherDao;
    private LocationDao locationDao;
    private LiveData<List<Weather>> weatherDataEntries;
    private LiveData<List<MonitorLocation>> locationData;

    /* for fetching remote data via managed, scheduled execution */
    private RemoteDataFetchModel remoteModel;

    /* package instant sensor reading into LiveData, separate from any db updates */
    private MutableLiveData<String> instantSensorReading = new MutableLiveData<>();

    /* potential singleton alternative: return static instance via getInstance(application) */
    /* singleton pattern used to avoid having open connections to web servers, APIs, caches etc */
    public WeatherRepository(Application application) {
        WeatherDatabase weatherDatabase  = WeatherDatabase.getInstance(application);
        LocationDatabase locationDatabase = LocationDatabase.getInstance(application);
        weatherDao = weatherDatabase.weatherDao();
        locationDao = locationDatabase.locationDao();
        weatherDataEntries = weatherDao.getAllWeatherPoints();
        locationData = locationDao.getLocationTable();
        instantSensorReading.setValue("VX;TX|");

        /* Instantiate background execution model. Need reference to application for GPS tasks */
        remoteModel = RemoteDataFetchModel.getInstance(weatherDao, locationDao, application,
                instantSensorReading);
    }

    /* Room sets up this database operation to run on a bg thread, according to CodingInFlow. */
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return weatherDataEntries;
    }

    public LiveData<List<MonitorLocation>> getLocationData() {return locationData;}

    public void updateLocationOnPrompt() {
        remoteModel.updateLocationOnPrompt();
    }

    public void updateSensorReadingOnPrompt(String parameter) {
        remoteModel.updateSensorReadingOnPrompt(parameter);
    }

    public MutableLiveData<String> getInstantSensorReading() {
        return instantSensorReading;
    }

    public List<Weather> getWeatherDataEntriesFromDb() {
        return remoteModel.getWeatherDataEntriesFromDb();
    }
}

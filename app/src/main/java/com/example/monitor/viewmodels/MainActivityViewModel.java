package com.example.monitor.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.WeatherRepository;

import java.util.List;

/* ViewModel for Temperature class */
public class MainActivityViewModel extends AndroidViewModel {

    /* observable data */
    private final MutableLiveData<Boolean> isUpdating /*= new MutableLiveData<>()*/;
    private LiveData<List<Weather>> immutableWeatherDataEntries;
    private LiveData<List<MonitorLocation>> locationData;

    /* repository module with which the ViewModel communicates */
    private WeatherRepository weatherRepository;

    /* no references to activity in ViewModel; needs application context to pass to repository,
    * needed to instantiate database  */
    public MainActivityViewModel(@NonNull Application application) {
        super(application);
//        if (mutableWeatherDataEntries != null) {
//            return;
//        }

        /* get weather repository object; alternative implement via getInstance? */
        weatherRepository = new WeatherRepository(application);
        immutableWeatherDataEntries = weatherRepository.getWeatherDataEntries();
        locationData = weatherRepository.getLocationData();
        isUpdating = weatherRepository.getIsUpdating();

        /* here the repository can expose controls to the RemoteDataFetchModel
        * which it initiated */
    }

    public LiveData<Boolean> getIsUpdating() {
        return isUpdating;
    }

    /* for access to weather data */
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return immutableWeatherDataEntries;
    }

    public LiveData<List<MonitorLocation>> getLocationData(){return locationData;};

    public void insert(Weather weatherDataPoint) {
        isUpdating.setValue(true); /* may also be set false here or in worker thread if possible */
        weatherRepository.insert(weatherDataPoint);
    }

}

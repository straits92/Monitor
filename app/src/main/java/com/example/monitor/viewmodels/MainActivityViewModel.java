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

    /* there should be no references to activity in ViewModel;
    but needs application context to pass to repository, needed to instantiate database  */
    public MainActivityViewModel(@NonNull Application application) {
        super(application);
//        if (mutableWeatherDataEntries != null) {
//            return;
//        }

        /* weather repository object shouldn't need reference to UI? */
        weatherRepository = new WeatherRepository(application);

        /* repository passes its LiveData to ViewModel, which passes it to MainActivity. But
        * would the ViewModel or MainActivity see any changes done on the database if those are not
        * done in terms of a LiveData object? */
        immutableWeatherDataEntries = weatherRepository.getWeatherDataEntries();
        locationData = weatherRepository.getLocationData();
        isUpdating = weatherRepository.getIsUpdating();

        /* here the repository can expose controls to the RemoteDataFetchModel
        * which it initiated */
    }

    /* convert RxJava-maintained data from the repository, into LiveData (using LiveDataReactiveStreams?) */

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

    public void updateLocationOnPrompt() {
        weatherRepository.updateLocationOnPrompt();
    }

}

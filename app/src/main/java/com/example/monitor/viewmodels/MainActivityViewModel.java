package com.example.monitor.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.models.Temperature;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.DataRepository;
import com.example.monitor.repositories.WeatherRepository;

import java.util.ArrayList;
import java.util.List;

/* ViewModel for Temperature class */
public class MainActivityViewModel extends AndroidViewModel {

    /* observable data */
    private MutableLiveData<ArrayList<Temperature>> mutableTempDataEntries;
    private final MutableLiveData<Boolean> isUpdating /*= new MutableLiveData<>()*/;
    private LiveData<List<Weather>> unmutableWeatherDataEntries;

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
        unmutableWeatherDataEntries = weatherRepository.getWeatherDataEntries();
        isUpdating = weatherRepository.getIsUpdating();

        /* here the repository can expose controls to the RemoteDataFetchModel
        * which it initiated */
    }

    public LiveData<Boolean> getIsUpdating() {
        return isUpdating;
    }

    /* for access to weather data */
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return unmutableWeatherDataEntries;
    }

    public void insert(Weather weatherDataPoint) {
        isUpdating.setValue(true); /* may also be set false here or in worker thread if possible */
        weatherRepository.insert(weatherDataPoint);
    }

}

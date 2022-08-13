package com.example.monitor.viewmodels;

import android.app.Application;
import android.nfc.Tag;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.WeatherRepository;
import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.repositories.networkutils.TopicData;

import java.util.List;

/* ViewModel for temperature data */
public class MainActivityViewModel extends AndroidViewModel {
    private static final String TAG = "MainActivityViewModel";

    /* observable data */
    private LiveData<List<Weather>> immutableWeatherDataEntries;
    private LiveData<List<MonitorLocation>> locationData;

    /* mqtt */
    private MQTTConnection MQTTmodule;

    /* package instant sensor reading into LiveData, separate from any db updates */
    private MutableLiveData<String> instantSensorReading;

    /* repository module with which the ViewModel communicates */
    private WeatherRepository weatherRepository;

    /* application context needed in repository, to instantiate database  */
    public MainActivityViewModel(@NonNull Application application) {
        super(application);
        weatherRepository = new WeatherRepository(application);

        /* set up connecting variables to the repository */
        /* LiveData flow: Database -> Repository -> ViewModel -> MainActivity  */
        immutableWeatherDataEntries = weatherRepository.getWeatherDataEntries();
        locationData = weatherRepository.getLocationData();
        instantSensorReading = weatherRepository.getInstantSensorReading();

        /* initialize MQTT for the entire app */
        MQTTmodule = MQTTConnection.getInstance(); // no need for the reference at this point
        MQTTConnection.connectAsync();
    }

    /*** wrapper methods used in the MainActivity ***/
    public LiveData<List<Weather>> getWeatherDataEntries() {
        return immutableWeatherDataEntries;
    }

    public LiveData<List<MonitorLocation>> getLocationData(){return locationData;};

    public void updateLocationOnPrompt() {
        weatherRepository.updateLocationOnPrompt();
    }

    public void updateSensorReadingOnPrompt(String parameter) {
        weatherRepository.updateSensorReadingOnPrompt(parameter);
    }

    public LiveData<String> getInstantSensorReading() { return instantSensorReading; }

    public List<Weather> getWeatherDataEntriesFromDb() {
        return weatherRepository.getWeatherDataEntriesFromDb();
    }
}

package com.example.monitor.repositories.execmodel;

import android.util.Log;

import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;

import java.util.List;
import java.util.concurrent.Callable;

/* identical task may be usable for caching weather data ? */
public class cachingLocationOrWeatherDataTask implements Callable<String> {
    private static final String TAG = "cachingLocationDataTask";

    private List<MonitorLocation> monitorLocationList = null;
    private LocationDao locationDaoReference = null;
    private List<Weather> weatherList = null;
    private WeatherDao weatherDaoReference = null;

    public cachingLocationOrWeatherDataTask(List<MonitorLocation> monitorLocationList, LocationDao locationDaoReference) {
        this.monitorLocationList = monitorLocationList;
        this.locationDaoReference = locationDaoReference;
    }

    public cachingLocationOrWeatherDataTask(List<Weather> weatherList, WeatherDao weatherDaoReference) {
        this.weatherList = weatherList;
        this.weatherDaoReference = weatherDaoReference;
    }

    public synchronized void cacheLocationData(List<MonitorLocation> monitorLocationList) {
        locationDaoReference.deleteLocationTable();
        locationDaoReference.insertLocationList(monitorLocationList);
    }

    public synchronized void cacheWeatherData(List<Weather> weatherList) {
        weatherDaoReference.deleteAllWeatherPoints();
        weatherDaoReference.insertWeatherList(weatherList);
    }

    @Override
    public String call() throws Exception {

        if (monitorLocationList != null) {
            cacheLocationData(monitorLocationList);
            Log.i(TAG, "Location data cached.");
        }

        if (weatherList != null) {
            cacheWeatherData(weatherList);
            Log.i(TAG, "Weather data cached.");
        }


        return null;
    }
}

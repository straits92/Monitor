package com.example.monitor.repositories.execmodel;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class CacheDataInDbsTask implements Callable<String> {
    private static final String TAG = "cachingLocationOrWeatherDataTask";

    private List<MonitorLocation> monitorLocationList = null;
    private LocationDao locationDaoReference = null;
    private MonitorLocation location = null;

    private List<Weather> weatherList = null;
    private Weather weatherDataPoint = null;
    private WeatherDao weatherDaoReference = null;
    private boolean shouldClearWeatherCache = false;

    public CacheDataInDbsTask(MonitorLocation fetchedLocation, LocationDao locationDaoReference) {
        this.location = fetchedLocation;
        this.locationDaoReference = locationDaoReference;
    }

    public CacheDataInDbsTask(List<Weather> weatherList, WeatherDao weatherDaoReference, boolean shouldClearWeatherCache) {
        this.shouldClearWeatherCache = shouldClearWeatherCache;
        this.weatherList = weatherList;
        this.weatherDaoReference = weatherDaoReference;
    }

    public CacheDataInDbsTask(Weather weatherDataPoint, WeatherDao weatherDaoReference, boolean shouldClearWeatherCache) {
        this.shouldClearWeatherCache = shouldClearWeatherCache;
        this.weatherDataPoint = weatherDataPoint;
        this.weatherDaoReference = weatherDaoReference;
    }


    public synchronized void cacheLocationData(List<MonitorLocation> monitorLocationList) {
        locationDaoReference.deleteLocationTable();
        locationDaoReference.insertLocationList(monitorLocationList);
    }

    public synchronized void cacheWeatherDataList(List<Weather> weatherList) {
        if (shouldClearWeatherCache) {
            weatherDaoReference.deleteAllWeatherPoints();
        }
        weatherDaoReference.insertWeatherList(weatherList);
    }

    public synchronized void cacheWeatherDataPoint(Weather weatherPoint) {
        if (shouldClearWeatherCache) {
            weatherDaoReference.deleteAllWeatherPoints();
        }

        // does it insert as last, most recent?
        weatherDaoReference.insert(weatherPoint);
    }

    @SuppressLint("LongLogTag")
    @Override
    public String call() throws Exception {

        if (location != null) {
            Log.d(TAG, "Location data updating...");

            List<MonitorLocation> listReference = locationDaoReference.getLocationTable().getValue();
            if (listReference == null) {
                Log.d(TAG, "Fetched list from location db is null again!");
            }

            List<MonitorLocation> listCopy = new ArrayList<>();
            listCopy.add(location.getLocationType(), location);
            cacheLocationData(listCopy);
            Log.d(TAG, "Location data should be updated now.");

            return null;
        }

        if (weatherList != null) {
            cacheWeatherDataList(weatherList);
            Log.i(TAG, "Weather data cached.");
            return null;
        }

        if (weatherDataPoint != null){
            cacheWeatherDataPoint(weatherDataPoint);
        }

        return null;
    }
}

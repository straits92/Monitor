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
    private static final String TAG = "CacheDataInDbsTask";

    private List<MonitorLocation> monitorLocationList = null;
    private LocationDao locationDaoReference = null;
    private MonitorLocation location = null;

    private List<Weather> weatherList = null;
    private Weather weatherDataPoint = null;
    private WeatherDao weatherDaoReference = null;
    private boolean shouldClearWeatherCache = false;

    /* constructors */
    public CacheDataInDbsTask(MonitorLocation fetchedLocation, LocationDao locationDaoReference) {
        Log.d(TAG, "CacheDataInDbsTask: task instantiated with location");
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


    /* caching routines */
    public synchronized void cacheLocationData(/*MonitorLocation locationEntry*/) {

        List<MonitorLocation> tempList = locationDaoReference.getLocationTableNonLive();
        Log.d(TAG, "cacheLocationData: attempt to UPDATE location as opposed to  delete+insert");
        location.setId(tempList.get(0).getId());
        locationDaoReference.update(location);

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



    /* overridden call method for submitting task object to executor */
    @SuppressLint("LongLogTag")
    @Override
    public String call() throws Exception {

        if (location != null) {
            Log.d(TAG, "call: location provided, attempt to cache it");
            List<MonitorLocation> listReferenceNonLive = locationDaoReference.getLocationTableNonLive();

            /* update logic intended to handle a 1-entry location list */
            if (listReferenceNonLive != null && listReferenceNonLive.size() > 0) {
                if (listReferenceNonLive.get(0).getLocation() != location.getLocation()) {
                    cacheLocationData(/*location*/);
                } else {
                    return null;
                }
            } else {
                Log.d(TAG, "cacheLocationData: getLocationTableNonLive() returned null, do delete+insert");
                if (this.monitorLocationList == null) {
                    this.monitorLocationList = new ArrayList<>();
                    this.monitorLocationList.add(location);
                }
                locationDaoReference.deleteLocationTable();
                locationDaoReference.insertLocationList(monitorLocationList);
            }

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

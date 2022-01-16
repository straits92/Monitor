package com.example.monitor.repositories.execmodel;

import android.annotation.SuppressLint;
import android.util.Log;

import com.example.monitor.databases.LocationDao;
import com.example.monitor.databases.WeatherDao;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;

import java.util.ArrayList;
import java.util.Iterator;
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
    private boolean storeWeatherDataIteratively = false;

    /* constructors for caching of different data types */
    public CacheDataInDbsTask(MonitorLocation fetchedLocation, LocationDao locationDaoReference) {
        Log.d(TAG, "CacheDataInDbsTask: task instantiated with location");
        this.location = fetchedLocation;
        this.locationDaoReference = locationDaoReference;
    }

    public CacheDataInDbsTask(List<Weather> weatherList, WeatherDao weatherDaoReference, boolean shouldClearWeatherCache, boolean storeWeatherDataIteratively) {
        this.shouldClearWeatherCache = shouldClearWeatherCache;
        this.storeWeatherDataIteratively = storeWeatherDataIteratively;
        this.weatherList = weatherList;
        this.weatherDaoReference = weatherDaoReference;
    }

    public CacheDataInDbsTask(Weather weatherDataPoint, WeatherDao weatherDaoReference, boolean shouldClearWeatherCache) {
        this.shouldClearWeatherCache = shouldClearWeatherCache;
        this.weatherDataPoint = weatherDataPoint;
        this.weatherDaoReference = weatherDaoReference;
    }
    
    /* ... and their corresponding caching routines */
    public synchronized void cacheLocationData(/*MonitorLocation locationEntry*/) {

        List<MonitorLocation> tempList = locationDaoReference.getLocationTableNonLive();
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

        weatherDaoReference.insert(weatherPoint);
    }

    private void cacheWeatherDataListIteratively(List<Weather> weatherList) {

        boolean printDataInfo = false;
        Iterator iter = weatherList.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();
            weatherDaoReference.update(weatherEntryInIter);
        }

        if (printDataInfo) {
            Log.d(TAG, "cacheWeatherDataListIteratively: weather list:");}
        List<Weather> weatherListNonLive = weatherDaoReference.getAllWeatherPointsNonLive();
        Iterator readiter = weatherListNonLive.iterator();
        while (readiter.hasNext()) {
            Weather weatherEntryInIter = (Weather) readiter.next();
            Integer elementIndex = weatherListNonLive.indexOf(weatherEntryInIter);
            if (printDataInfo) {
                Log.i(TAG, "\nElement index: " + elementIndex
                        + "\nDateTime: " + weatherEntryInIter.getTime()
                        + "\nID: " + weatherEntryInIter.getId()
                        + "\nPersistence: " + weatherEntryInIter.getPersistence());
            }

        }

    }


    /* overridden call method for submitting task object to executor */
    @SuppressLint("LongLogTag")
    @Override
    public String call() throws Exception {

        if (location != null) {
            Log.d(TAG, "call: location provided, to be cached");
            List<MonitorLocation> listReferenceNonLive = locationDaoReference.getLocationTableNonLive();

            /* update logic intended to handle a 1-entry location list */
            if (listReferenceNonLive != null && listReferenceNonLive.size() > 0) {
                if (listReferenceNonLive.get(0).getLocation() != location.getLocation()) {
                    cacheLocationData(/*location*/);
                } else {
                    return null;
                }
            } else {

                if (this.monitorLocationList == null) {
                    this.monitorLocationList = new ArrayList<>();
                    this.monitorLocationList.add(location);
                }
                locationDaoReference.deleteLocationTable();
                locationDaoReference.insertLocationList(monitorLocationList);
            }
            return null;
        }
        
        if (weatherList != null) {
            if (storeWeatherDataIteratively) {
                cacheWeatherDataListIteratively(weatherList);
            } else {
                cacheWeatherDataList(weatherList);
            }
            return null;
        }

        if (weatherDataPoint != null){
            cacheWeatherDataPoint(weatherDataPoint);
        }

        return null;
    }

}

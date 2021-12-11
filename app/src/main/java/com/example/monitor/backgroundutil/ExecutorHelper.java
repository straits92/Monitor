package com.example.monitor.backgroundutil;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/* the fact that it has static executor members, each with an associated static instantiator,
* suggests that the attempt to create multiple single instantiators only returns the one */
public class ExecutorHelper {
    private static final String TAG = "ExecutorHelper";

    /* singletons */
    private static ScheduledExecutorService scheduledExecutorInstance;
    private static ExecutorService networkRequestExecutorInstance;
    private static ExecutorService databaseExecutorInstance;
    private static ExecutorService gpsExecutorInstance;
    private static ExecutorService serviceExecutor;

    public static synchronized ScheduledExecutorService getScheduledPoolInstance(){
        if(scheduledExecutorInstance == null){
            int coreCount = Runtime.getRuntime().availableProcessors();
            Log.d(TAG, "scheduledExecutor created. # of threads (== # of cores): "+ coreCount);
            /* decide on thread qty based on nr of expected IO tasks instead */
            scheduledExecutorInstance = Executors.newScheduledThreadPool(coreCount);
        }
        return scheduledExecutorInstance;
    }

    public static synchronized ExecutorService getNetworkRequestExecutorInstance(){
        if(networkRequestExecutorInstance == null){
            Log.d(TAG, "networkRequestExecutor created.");
            networkRequestExecutorInstance = Executors.newSingleThreadExecutor();
            return networkRequestExecutorInstance;
        }
        Log.d(TAG, "existing networkRequestExecutor returned.");
        return networkRequestExecutorInstance;
    }

    public static synchronized ExecutorService getDatabaseExecutorInstance(){
        if(databaseExecutorInstance == null){
            Log.d(TAG, "databaseExecutorInstance created.");
            databaseExecutorInstance = Executors.newSingleThreadExecutor();
            return databaseExecutorInstance;
        }
        Log.d(TAG, "existing databaseExecutorInstance returned.");
        return databaseExecutorInstance;
    }

    public static synchronized ExecutorService getServiceExecutorInstance(){
        if(serviceExecutor == null){
            Log.d(TAG, "serviceExecutor created for miscellaneous tasks.");
            serviceExecutor = Executors.newSingleThreadExecutor();
            return serviceExecutor;
        }
        Log.d(TAG, "existing serviceExecutor returned.");
        return serviceExecutor;
    }

    public static synchronized ExecutorService getGpsExecutorInstance(){
        if(gpsExecutorInstance == null){
            Log.d(TAG, "gpsExecutorInstance created as cachedThreadPool; kill thread if no tasks for 60 seconds.");
            gpsExecutorInstance = Executors.newCachedThreadPool();

            return gpsExecutorInstance;
        }
        Log.d(TAG, "existing gpsExecutorInstance returned.");
        return gpsExecutorInstance;
    }





}
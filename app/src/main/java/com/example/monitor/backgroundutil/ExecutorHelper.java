package com.example.monitor.backgroundutil;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/* helper class for creating threads to do background tasks */
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
            networkRequestExecutorInstance = Executors.newSingleThreadExecutor();
            return networkRequestExecutorInstance;
        }
        return networkRequestExecutorInstance;
    }

    public static synchronized ExecutorService getDatabaseExecutorInstance(){
        if(databaseExecutorInstance == null){
            databaseExecutorInstance = Executors.newSingleThreadExecutor();
            return databaseExecutorInstance;
        }
        return databaseExecutorInstance;
    }

    public static synchronized ExecutorService getServiceExecutorInstance(){
        if(serviceExecutor == null){
            serviceExecutor = Executors.newSingleThreadExecutor();
            return serviceExecutor;
        }
        return serviceExecutor;
    }

    public static synchronized ExecutorService getGpsExecutorInstance(){
        if(gpsExecutorInstance == null){
            gpsExecutorInstance = Executors.newCachedThreadPool();
            return gpsExecutorInstance;
        }
        return gpsExecutorInstance;
    }





}
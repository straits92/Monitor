package com.example.monitor.backgroundutil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ExecutorHelper {
    /* singleton */
    private static ExecutorService singleInstance;
    private static ScheduledExecutorService scheduledInstance;
    public static synchronized ExecutorService getSingleThreadExecutorInstance(){
        if(singleInstance == null){
            singleInstance = Executors.newSingleThreadExecutor();
        }
        return singleInstance;
    }
    public static synchronized  ScheduledExecutorService getScheduledPool(){
        int coreCount = Runtime.getRuntime().availableProcessors();
        if(scheduledInstance == null){
            /* decide on thread qty based on nr of expected IO tasks instead */
            scheduledInstance = Executors.newScheduledThreadPool(coreCount);
        }
        return scheduledInstance;

    }

}
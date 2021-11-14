package com.example.monitor.backgroundutil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorHelper {
    /* singleton */
    private static ExecutorService instance;
    public static synchronized ExecutorService getSingleThreadExecutorInstance(){
        if(instance == null){
            instance = Executors.newSingleThreadExecutor();
        }
        return instance;
    }
}
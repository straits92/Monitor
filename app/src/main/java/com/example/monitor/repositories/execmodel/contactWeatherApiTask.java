package com.example.monitor.repositories.execmodel;

import android.util.Log;

import com.example.monitor.repositories.networkutils.NetworkUtils;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/* maybe this should be outside of the remoteModel execution model, so that it
 * can be constructed multiple times and have URL passed to it. if it is static,
 * then it may only be constructed once, which means that URL can't be passed to each object. */
public class contactWeatherApiTask implements Callable<String> {
    private static final String TAG = "contactWeatherApiTask";

    private URL requestUrl;
    public contactWeatherApiTask (URL requestUrl) {
        this.requestUrl = requestUrl;
    }

    @Override
    public String call() /*throws ExecutionException, InterruptedException*/ {
        String response = null;

        /* get String response from remote: requests for location and weather data are decoupled */
        Log.i(TAG, "URL requested: "+requestUrl.toString());
        try {
            /* the count of requests should reset to 0 every 24 hours */
            // requestCounts++;
            response = NetworkUtils.getResponseFromHttpUrl(requestUrl);
            Log.i(TAG, "response from API: " + response);
        } catch (IOException e){
            Log.i(TAG, "fetching failed");
            e.printStackTrace();
        }

        return response;
    }
}

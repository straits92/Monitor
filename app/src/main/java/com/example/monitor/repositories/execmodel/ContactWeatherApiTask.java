package com.example.monitor.repositories.execmodel;

import android.util.Log;

import com.example.monitor.repositories.networkutils.NetworkUtils;
import com.example.monitor.repositories.parseutils.JSONUtils;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class ContactWeatherApiTask implements Callable<String> {
    private static final String TAG = "contactWeatherApiTask";

    private URL requestUrl;
    public ContactWeatherApiTask(URL requestUrl) {
        this.requestUrl = requestUrl;
    }

    @Override
    public String call() {
        String response = null;
        Log.i(TAG, "URL requested: "+requestUrl.toString());
        try {
            response = NetworkUtils.getResponseFromHttpUrl(requestUrl);
            Log.i(TAG, "response from API or LAN: " + response);
        } catch (IOException e){
            Log.i(TAG, "fetching failed: maybe ran out of free API requests for the day; or LAN issues");
            e.printStackTrace();
        }

        // check if valid json response
        if (JSONUtils.isJSONValid(response) == true)
            return response;
        else {
            Log.i(TAG, "Response is not in valid JSON format.");
            return null;
        }
    }
}

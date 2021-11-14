package com.example.monitor.repositories.execmodel;

import com.example.monitor.models.Weather;

import java.util.List;

/* So some overall scheduler could run in the background and issue network calls
 * for accuweather every hour. but this call itself should be cancelled if the
 * response is taking more than a few seconds, and skipped? then both the skipped
 * and the next hour could be tried in the future, to compensate?
 */
public class RemoteDataFetchModel {
    private static RemoteDataFetchModel instance;

    /* singleton */
    public static RemoteDataFetchModel getInstance() {
        if (instance == null){
            instance = new RemoteDataFetchModel();
        }
        return instance;
    }

    // get network utilities

    // get parseJSON utilities

    // method for an initial fetching and parsing of remote data

    // method to initiate scheduling logic

    // methods that package the parsed data from remote ...

    // use Data Access Object functionality to operate on live data

}

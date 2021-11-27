package com.example.monitor.repositories.networkutils;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

/* methods need to be called in background threads */
public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    /* Accuweather API artefacts */
    /* location info to match GPS lat,lon with cities tagged by accuweather */
    private static final String WEATHERDB_GEOPOSITION =
            "http://dataservice.accuweather.com/locations/v1/cities/geoposition/search";

    /* hourly requests authorized by free usage of API */
    private static final String WEATHERDB_BASE_URL_1HOUR =
            "http://dataservice.accuweather.com/forecasts/v1/hourly/1hour/";
    private static final String WEATHERDB_BASE_URL_12HOURS =
            "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/";

    private static final String API_KEY = "Tw9ezktZFbGcFwPa5DfZ8NjoEs2st0ah";
    private static final String PARAM_API_KEY = "apikey";
    private static final String PARAM_METRIC_KEY = "metric";
    private static final String PARAM_LOC = "q";

    public static URL buildUrlForLocation(String latitude, String longitude) {

        Uri builtUri = Uri.parse(WEATHERDB_GEOPOSITION).buildUpon()
                .appendQueryParameter(PARAM_API_KEY, API_KEY)
                .appendQueryParameter(PARAM_LOC, latitude+","+longitude) /* obtained from GPS; comma may be %2C  */
                .build();

        URL url = null;
        try {
            url = new URL(builtUri.toString());
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }

//        Log.i(TAG,"BuildUrlForLocation: url: "+url);
        return url;
    }

    public static URL buildUrlForWeather(int forecastType, String location) {
        String combinedURL;
        String requestScheme;

        if (forecastType == 0) {
            requestScheme = WEATHERDB_BASE_URL_12HOURS;
        } else {
            requestScheme = WEATHERDB_BASE_URL_1HOUR;
        }

        combinedURL = requestScheme+location;
        Uri builtUri = Uri.parse(combinedURL).buildUpon()
                .appendQueryParameter(PARAM_API_KEY, API_KEY)
                .appendQueryParameter(PARAM_METRIC_KEY, "true") /* request temperature in Celsius */
                .build();

        URL url = null;
        try {
            url = new URL(builtUri.toString());
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }

//        Log.i(TAG,"BuildUrlForWeather: url: "+url);
        return url;
    }

    /* should return the entire API response as a string; should be called in background thread */
    public static String getResponseFromHttpUrl(URL url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        String tempStore = null;
        try {
            InputStream in = urlConnection.getInputStream();

            Scanner scanner = new Scanner(in);
            scanner.useDelimiter("\\A");

            boolean hasInput = scanner.hasNext();
            if (hasInput){
                tempStore = scanner.next();
                return tempStore;
            } else {
                return null;
            }
        } finally {
            urlConnection.disconnect();
        }

    }

}

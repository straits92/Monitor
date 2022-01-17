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

    /* hourly requests via Accuweather API */
    private static final String WEATHERDB_BASE_URL_1HOUR =
            "http://dataservice.accuweather.com/forecasts/v1/hourly/1hour/";
    private static final String WEATHERDB_BASE_URL_12HOURS =
            "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/";

    private static final String API_KEY = "Tw9ezktZFbGcFwPa5DfZ8NjoEs2st0ah";
    private static final String PARAM_API_KEY = "apikey";
    private static final String PARAM_METRIC_KEY = "metric";
    private static final String PARAM_LOC = "q";
    private static final String PARAM_DETAILS = "details";

    /* hourly requests towards LAN server on Raspberry Pi connected to sensor, tunneled via ngrok */
    private static final String LAN_IP_PI_ZERO = "192.168.1.157";
    private static final String LAN_IP_PI_4B = "192.168.1.158";
    private static final String LAN_URL_1HOUR = "http://"+LAN_IP_PI_4B+"/sensordata_hourly.json";
    private static final String LAN_URL_INSTANT = "http://"+LAN_IP_PI_4B+"/sensordata_instant.json";

    private static final String NGROK_TUNNEL_LINK_TEMPORARY = "http://98a3-178-220-204-81.ngrok.io/";
    private static final String NGROK_URL_1HOUR = NGROK_TUNNEL_LINK_TEMPORARY+"sensordata_hourly.json";
    private static final String NGROK_URL_INSTANT = NGROK_TUNNEL_LINK_TEMPORARY+"sensordata_instant.json";


    public static URL buildUrlForLocation(String latitude, String longitude) {

        Uri builtUri = Uri.parse(WEATHERDB_GEOPOSITION).buildUpon()
                .appendQueryParameter(PARAM_API_KEY, API_KEY)
                /* obtained from GPS; comma may be %2C  */
                .appendQueryParameter(PARAM_LOC, latitude+","+longitude)
                .build();

        URL url = null;
        try {
            url = new URL(builtUri.toString());
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }

        return url;
    }

    public static URL buildUrlForWeather(int forecastType, String location) {
        String requestScheme;
        Uri builtUri = null;

        if (forecastType == 0) {
            requestScheme = WEATHERDB_BASE_URL_12HOURS;
            builtUri = Uri.parse(requestScheme+location).buildUpon()
                    .appendQueryParameter(PARAM_API_KEY, API_KEY)
                    .appendQueryParameter(PARAM_METRIC_KEY, "true") /* request temperature in Celsius */
                    .build();
        } else if (forecastType == 1){
            requestScheme = WEATHERDB_BASE_URL_1HOUR;
            builtUri = Uri.parse(requestScheme+location).buildUpon()
                    .appendQueryParameter(PARAM_API_KEY, API_KEY)
                    .appendQueryParameter(PARAM_METRIC_KEY, "true") /* request temperature in Celsius */
//                .appendQueryParameter(PARAM_DETAILS, "true") /* request full details */
                    .build();
        } else if (forecastType == 2) {
            requestScheme = NGROK_URL_1HOUR; // LAN_URL_1HOUR
            builtUri = Uri.parse(requestScheme).buildUpon().build();
        } else if (forecastType == 3) {
            requestScheme = NGROK_URL_INSTANT; // LAN_URL_INSTANT
            builtUri = Uri.parse(requestScheme).buildUpon().build();
        } else {
            requestScheme = WEATHERDB_BASE_URL_1HOUR; // default
            builtUri = Uri.parse(requestScheme+location).buildUpon()
                    .appendQueryParameter(PARAM_API_KEY, API_KEY)
                    .appendQueryParameter(PARAM_METRIC_KEY, "true") /* request temperature in Celsius */
                    .build();
        }

        URL url = null;
        try {
            url = new URL(builtUri.toString());
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }

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

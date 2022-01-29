package com.example.monitor.repositories.parseutils;

import android.util.Log;

import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/* JSON key strings specifically for accuweather JSON objects, or local sensors */
public class ParseUtils {
    private static final String TAG = "ParseUtils: ";

    /* JSON parse for weather, may return one or more data points from accuweather API or sensor */
    public static List<Weather> parseWeatherJSON(String weatherSearchResults) {
        List<Weather> weatherArrayList = new ArrayList<Weather>();
        if (weatherSearchResults != null) {
            try {
                /* Hourly request: response is an entire array (for days, response is JSONObj)*/
                JSONArray results = new JSONArray(weatherSearchResults);

                /* extract weather data for each hour, construct data point, add to array */
                for (int i = 0; i < results.length(); i++) {
                    Weather weather = new Weather(null, null, null, null,
                            null,null, null, 0);

                    /* set data obtained from network response; not resultant analytical data */
                    JSONObject singleEntry = results.getJSONObject(i);

                    /* check if JSON contains error msg as defined by Accuweather API */
                    if (singleEntry.has("Message") && singleEntry.has("Code")) {
                        Log.i(TAG, "parseWeatherJSON: ERROR RESPONSE: "
                                +singleEntry.get("Message"));
                        return null;
                    }

                    String time = singleEntry.getString("DateTime");
                    weather.setTime(time);

                    JSONObject temperatureObj = singleEntry.getJSONObject("Temperature");
                    String link = singleEntry.getString("Link");
                    weather.setLink(link);

                    String tempVal = temperatureObj.getString("Value");
                    weather.setCelsius(tempVal);

                    String humidityVal = singleEntry.getString("RelativeHumidity");
                    weather.setHumidity(humidityVal);
                    /* further weather info can be extracted from object, like indoor relhumidity */

                    weatherArrayList.add(weather);
                }
                return weatherArrayList;
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        return null;
    }

    /* JSON parse for location; returns the location object, not a list */
    public static MonitorLocation parseLocationJSON(String locationSearchResults) {
        String locationString;
        String localizedName;
        String latitude;
        String longitude;
        MonitorLocation monitorLocation = new MonitorLocation(null, null,
                null,null, false, 0);

        if (locationSearchResults != null) {
            try {
                /* Location request: response is an object */
                JSONObject result = new JSONObject(locationSearchResults);
                JSONObject geopositionObj = result.getJSONObject("GeoPosition");

                /* check if JSON contains error msg */
                if (geopositionObj.has("Message") && geopositionObj.has("Code")) {
                    Log.i(TAG, "parseLocationJSON: ERROR RESPONSE: "
                            +geopositionObj.get("Message"));
                    return null;
                }

                latitude = geopositionObj.getString("Latitude");
                longitude = geopositionObj.getString("Longitude");
                locationString = result.getString("Key");
                localizedName = result.getString("LocalizedName");

                /* set data obtained from the network response; not resultant analytical data */
                monitorLocation.setLocation(locationString);
                monitorLocation.setLocalizedName(localizedName);
                monitorLocation.setLatitude(latitude);
                monitorLocation.setLongitude(longitude);
                monitorLocation.setGpsAvailable(false);

                return monitorLocation;
            } catch (JSONException e) {
                Log.d(TAG, "parseLocationJSON: parsing of response failed");
                e.printStackTrace();
            }
        }
        return null;
    }
}

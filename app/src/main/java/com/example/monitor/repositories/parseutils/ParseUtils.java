package com.example.monitor.repositories.parseutils;

import android.util.Log;

import com.example.monitor.models.Location;
import com.example.monitor.models.Weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* JSON key strings specifically for accuweather JSON objects */
public class ParseUtils {
    private static final String TAG = "ParseUtils: ";

    /* JSON parse for weather */
    /* issues:
    * instead of constructing List<Weather>, construct ArrayList<Weather>, and out of this
    * array extract details for weather data points one by one, and then insert them into
    * the database via Dao. */
    public static List<Weather> parseWeatherJSON(String weatherSearchResults) {
        List<Weather> weatherArrayList = new ArrayList<Weather>();
        if (weatherSearchResults != null) {
            try {
                /* Hour-based request: response is an entire array (for days, response is JSONObj)*/
                JSONArray results = new JSONArray(weatherSearchResults);

                /* DO IN WORKER: extract weather data for each hour, construct data point, add to array */
                for (int i = 0; i < results.length(); i++) {
                    Weather weather = new Weather(null, null, null);

                    JSONObject singleEntry = results.getJSONObject(i);
                    String time = singleEntry.getString("DateTime");
                    weather.setTime(time);

                    JSONObject temperatureObj = singleEntry.getJSONObject("Temperature");
                    String link = singleEntry.getString("Link");
                    weather.setLink(link);

                    String tempVal = temperatureObj.getString("Value");
                    weather.setCelsius(tempVal);

                    weatherArrayList.add(weather);
                }

                /* development info only: code prints log info  */
//                Iterator iter = weatherArrayList.iterator();
//                while (iter.hasNext()) {
//                    Weather weatherEntryInIter = (Weather) iter.next();
//                    Log.i(TAG, "onPostExecute: time: "+weatherEntryInIter.getTime() + " " +
//                            "Temperature: "+weatherEntryInIter.getCelsius() + " " +
//                            "Link: "+weatherEntryInIter.getLink());
//                }

                return weatherArrayList;

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        return null;
    }

    /* JSON parse for location */
    public static List<Location> parseLocationJSON(String locationSearchResults) {
        String locationString;
        String localizedName;
        String latitude;
        String longitude;
        List<Location> locationArrayList = new ArrayList<Location>();
        Location location = new Location(null, null, null,
                null, false);

        if (locationSearchResults != null) {
            try {
                /* Location request: response is an object */
                JSONObject result = new JSONObject(locationSearchResults);
                JSONObject geopositionObj = result.getJSONObject("GeoPosition");

                latitude = geopositionObj.getString("Latitude");
                longitude = geopositionObj.getString("Longitude");
                locationString = result.getString("Key");
                localizedName = result.getString("LocalizedName");

                location.setLocation(locationString);
                location.setLocalizedName(localizedName);
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                /* should GPS availability be set?  */

                locationArrayList.add(location);
                return locationArrayList;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}

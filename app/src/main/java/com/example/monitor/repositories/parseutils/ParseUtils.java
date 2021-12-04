package com.example.monitor.repositories.parseutils;

import android.util.Log;

import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/* JSON key strings specifically for accuweather JSON objects */
public class ParseUtils {
    private static final String TAG = "ParseUtils: ";

    /* JSON parse for weather; always returns a list because it may operate on multiple returns
    * from accuweather API or raspberry sensor */
    public static List<Weather> parseWeatherJSON(String weatherSearchResults) {
        List<Weather> weatherArrayList = new ArrayList<Weather>();
        if (weatherSearchResults != null) {
            try {
                /* Hour-based request: response is an entire array (for days, response is JSONObj)*/
                JSONArray results = new JSONArray(weatherSearchResults);

                /* DO IN WORKER: extract weather data for each hour, construct data point, add to array */
                for (int i = 0; i < results.length(); i++) {
                    Weather weather = new Weather(null, null, null, null, null, null);

                    /* set the data obtained from the network response; not resultant analytical data */
                    JSONObject singleEntry = results.getJSONObject(i);
                    String time = singleEntry.getString("DateTime");
                    weather.setTime(time);

                    JSONObject temperatureObj = singleEntry.getJSONObject("Temperature");
                    String link = singleEntry.getString("Link");
                    weather.setLink(link);

                    String tempVal = temperatureObj.getString("Value");
                    weather.setCelsius(tempVal);



                    /* further object decomposition if "detailed" url query was done:
                     * humidity
                     * wet bulb temp
                     * wind
                     * uv
                     * rain probability
                     * etc */


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

    /* JSON parse for location; returns the location object, not a list */
    public static MonitorLocation parseLocationJSON(String locationSearchResults) {
        String locationString;
        String localizedName;
        String latitude;
        String longitude;
        MonitorLocation monitorLocation = new MonitorLocation(null, null, null,
                null, false, 0);

        if (locationSearchResults != null) {
            try {
                /* Location request: response is an object */
                JSONObject result = new JSONObject(locationSearchResults);
                JSONObject geopositionObj = result.getJSONObject("GeoPosition");

                latitude = geopositionObj.getString("Latitude");
                longitude = geopositionObj.getString("Longitude");
                locationString = result.getString("Key");
                localizedName = result.getString("LocalizedName");

                /* set the data obtained from the network response; not resultant analytical data */
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

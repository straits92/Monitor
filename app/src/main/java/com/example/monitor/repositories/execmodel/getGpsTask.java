package com.example.monitor.repositories.execmodel;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

//import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Callable;

/* it is important that this task only runs on initiation, and at user button push.
 * because the application context may not exist for potential gps fetching in the
 * background, and this is needed to confirm permissions and potentially listen
 * for GPS update changes onClick. */
public class getGpsTask implements Callable<ArrayList<String>>, LocationListener {
    private static final String TAG = "getGpsTask";
    private static Application applicationFromModel;
    private LocationManager locationManager;

    Location final_location;
    Double longitude;
    Double latitude;
    ArrayList<String> passedLatLon;

    /* receive the lat,lon structure and change it, then return. */
    public getGpsTask(Application application, ArrayList<String> gpsLatLon) {
        applicationFromModel = application;
        passedLatLon = gpsLatLon;
        locationManager = (LocationManager) applicationFromModel.getSystemService(Context.LOCATION_SERVICE);

        /* default from gps task is Subotica */
        latitude = 46.1004; longitude = 19.6676;
        passedLatLon.add(0, latitude.toString()); passedLatLon.add(1, longitude.toString());
    }

    @Override
    public ArrayList<String> call() throws Exception {
//        ArrayList<String> tempLatLon = new ArrayList<>();

        // this is just a manifest check?
        if (ActivityCompat.checkSelfPermission(applicationFromModel, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(applicationFromModel, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                /*|| ActivityCompat.checkSelfPermission(applicationFromModel, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED*/) {
            Log.d(TAG, "call: no permission to access GPS data, defaulted to Subotica in constructor");
            return passedLatLon;
        }

        Location gps_location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//        network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if(gps_location != null && gps_location.getTime() > Calendar.getInstance().getTimeInMillis() - 120000) { // 2 * 60 * 1000
            final_location = gps_location;
            latitude = final_location.getLatitude();
            longitude = final_location.getLongitude();
            passedLatLon.add(0, latitude.toString());
            passedLatLon.add(1, longitude.toString());
            Log.d(TAG, "call: got last known location; lat: "+latitude.toString()+"; lon: "+longitude.toString());
        }
        else {
            /* java.util.concurrent.ExecutionException: java.lang.RuntimeException:
            Can't create handler inside thread Thread[pool-2-thread-1,5,main] that has
            not called Looper.prepare()
             */
            Looper.prepare();
            Log.d(TAG, "last known location returns null; need to requestLocationUpdates");

            /* trigger LocationListener's callback onLocationChanged if location changed by 5km */
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 5000, this);
        }

        return passedLatLon;
    }


    /* since this is a callback to the listener, its role in this app is ambiguous.
    * there is no guarantee that the passedLatLon will be updated by this callback
    * fast enough for the execution model to actually receive the data. GPS logic
    * requires reworking. */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (location != null) {
            Log.v("Location Changed", location.getLatitude() + " and " + location.getLongitude());

//            passedLatLon.add(0, latitude.toString());
//            passedLatLon.add(1, longitude.toString());

            /* maybe do cacheTask into location db right here? but it would also need
            * to query accuweather to get the city code etc. */
            locationManager.removeUpdates(this);
        }
    }
}

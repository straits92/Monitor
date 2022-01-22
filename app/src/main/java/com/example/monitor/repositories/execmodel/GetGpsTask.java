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

/* task only runs on initiation, and at user button push. the application context may not exist for
* potential gps fetching in the background, but is needed to confirm permissions and to listen for
* GPS update changes onClick. */
public class GetGpsTask implements Callable<ArrayList<String>>, LocationListener {
    private static final String TAG = "GetGpsTask";
    private static Application applicationFromModel;
    private LocationManager locationManager;

    Location final_location;
    Double longitude;
    Double latitude;
    ArrayList<String> passedLatLon;

    /* receive the lat,lon structure and change it, then return. */
    public GetGpsTask(Application application) {
        applicationFromModel = application;
        passedLatLon = new ArrayList<>();
        locationManager = (LocationManager) applicationFromModel
                .getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public ArrayList<String> call() throws Exception {
        /* check if Looper was prepared before; only ever call once within this thread */
        if (Looper.myLooper() == null) {
            Log.d(TAG, "Looper.prepare() called once to run GPS task in background thread.");
            Looper.prepare();
        }

        if (ActivityCompat.checkSelfPermission(applicationFromModel,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(applicationFromModel,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                /*|| ActivityCompat.checkSelfPermission(applicationFromModel,
                Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED*/) {
            Log.d(TAG, "call: no permission to access GPS data, return null");
            return null;
        }

        Location gps_location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//        network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if(gps_location != null && gps_location.getTime()
                > Calendar.getInstance().getTimeInMillis() - 12000) { // 2 * 60 * 1000, age of data
            final_location = gps_location;
            latitude = final_location.getLatitude();
            longitude = final_location.getLongitude();
            passedLatLon.add(0, latitude.toString());
            passedLatLon.add(1, longitude.toString());
            Log.d(TAG, "call: got last known location; lat: "+latitude.toString()
                    +"; lon: "+longitude.toString());
            return passedLatLon;
        }
        else { /* AMBIGUOUS BEHAVIOUR WITH REQUESTING LOCATION UPDATES */
            Log.d(TAG, "last known location returns null; need to requestLocationUpdates, " +
                    "but current behaviour is ambiguous, so the caller just defaults.");

            /* trigger LocationListener's callback onLocationChanged if location changed by >5km */
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
                    5000, this/*, Looper.getMainLooper()*/);
        }

        return null;
    }

    /* since this is a callback to the listener, its role in this app is unstable.
    * there is no guarantee that the passedLatLon will be updated by this callback
    * fast enough for the execution model to receive the data during instantiation.
    * This callback has not run thus far.  */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (location != null) {
            Log.v("onLocationChanged:", "callback with lat: "
                    +location.getLatitude() + " lon: " + location.getLongitude());
            passedLatLon.add(0, latitude.toString());
            passedLatLon.add(1, longitude.toString());
            locationManager.removeUpdates(this);
        }
    }
}

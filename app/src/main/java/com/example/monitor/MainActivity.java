package com.example.monitor;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.monitor.adapters.RecyclerWeatherAdapter;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
/* Monitor App: intended to fetch weather data from a weather station API,
* fetch temperature (and potentially other) data from a home temperature
* sensor governed by a Raspberry Pi set-up, and to display / compare these
* two sets of data on graphs. The data should be fetched on an hourly basis.
*  */

/* to-do list
 * handle object dependencies via dependency injection:
 *      (not implemented: used to reduce boilerplate code; does not preserve state.)
 *
 * implement a data cache queried by repository
 *      (seems to be made, and preserved, by Room functionality)
 *
 * implement network utilities for the web server data source
 *      (done for initial 12hr.)
 *
 * implement background hourly updates of the data
 * implement graphing display for data
 * have 2+ data arrays maintained for different data sources of temp
 *
 * later: query the local raspberry pi for temperature sensor data, store this data
 *
 * future extensions: humidity, other parameters
 *
 * */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /* declare app modules */
    private MainActivityViewModel temperatureViewModel;

    /* declare display elements here */
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Button homeLocation;

    /* dummy action button for updates to the list */
    private FloatingActionButton dummyFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* initiate display elements */
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        homeLocation = findViewById(R.id.homeLocation); /* should have an onClick too */

        /* initialize recycler view */
        /* alternative_adapter = new RecyclerAdapter(pass entries, pass this context); */
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
        recyclerView.setAdapter(weatherAdapter);

        /* maybe this permission is granted too slowly, and everything is instantiated
        * with location defaulting, before the user can react. so maybe gps activity
        * should only be triggered once the user permits. */
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);


        /* dummy action for simulating async addition of new data to LiveData */
        dummyFab = findViewById(R.id.floatingActionButton);
        dummyFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProgressBar();
                temperatureViewModel.insert(new Weather("200", "WEATHER_ENTRY", "100"));
            }
        });
        hideProgressBar();

        /* ViewModel scoped to lifecycle of this activity; androidOS destroys it when finished */
        temperatureViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* observe() is a livedata method, activity gets passed to it */
        temperatureViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
                Log.d(TAG, "Data observed from WeatherRepository (new).");
                Toast.makeText(MainActivity.this, "onChanged: weather data", Toast.LENGTH_SHORT).show();
                weatherAdapter.setWeatherRecyclerEntries(weathers);
                /* use notifyItemInserted, notifyItemRemoved */
            }
        });

        /* for data update progress bar tied to RecyclerView */
        temperatureViewModel.getIsUpdating().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (aBoolean) {
                    showProgressBar();
                } else {
                    hideProgressBar();
                    recyclerView.smoothScrollToPosition(0);/* first element in list, last inserted */
                }
            }
        });

        /* update location button/display; not recyclerview */
        temperatureViewModel.getLocationData().observe(this, new Observer<List<MonitorLocation>>(){
            @Override
            public void onChanged(@Nullable List<MonitorLocation> monitorLocations) {

                Log.d(TAG, "Data observed from LocationDatabase thru Weather Repository.");
                Toast.makeText(MainActivity.this, "onChanged: location", Toast.LENGTH_SHORT).show();
                String localizedName = temperatureViewModel.getLocationData().getValue().get(0).getLocalizedName();
                homeLocation.setText(localizedName);
                /* use notifyItemInserted, notifyItemRemoved, notifyDataSetChanged ?? adapter methods */
            }
        });


        Log.d(TAG, "onCreate: started.");
    }

    private void showProgressBar() { progressBar.setVisibility(View.VISIBLE);}
    private void hideProgressBar() { progressBar.setVisibility(View.INVISIBLE);}

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    Log.d(TAG, "GPS PERMISSION GRANTED BY USER: ");
                } else {
                    Log.d(TAG, "GPS PERMISSION DENIED BY USER: ");
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });
}
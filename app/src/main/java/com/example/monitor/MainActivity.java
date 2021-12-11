package com.example.monitor;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
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
    private Button variableLocation;

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
        variableLocation = findViewById(R.id.variableLocation);

        /* initialize recycler view showing the 12h forecast */
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

        /* DUMMY ACTION: for simulating async addition of new data to LiveData */
        dummyFab = findViewById(R.id.floatingActionButton);
        dummyFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProgressBar();
                temperatureViewModel.insert(new Weather("200", "WEATHER_ENTRY", "100", "298198", 0, 0));
            }
        });
        /* end dummy action */
        hideProgressBar();


        /* initialize ViewModel; ViewModel scoped to lifecycle of this activity; androidOS destroys
        * it when finished */
        temperatureViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* observe() is a livedata method, activity gets passed to it. onChanged is triggered
        * whenever anything in the LiveData from the database gets changed; not just
        * data points of a certain type. so even though only the hourly may be updated,
        * everything gets redrawn. */
        /* further, other data adapters will be needed on the main activity
         * and viewmodel level to show different types of data separately based
         * on the weather data point's category; 1hr, 12hr, or raspberry sensor */
        temperatureViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
                Log.d(TAG, "Data observed from WeatherRepository (new).");
                Toast.makeText(MainActivity.this, "onChanged: weather data", Toast.LENGTH_SHORT).show();
                weatherAdapter.setWeatherRecyclerEntries(weathers);
                /* use notifyItemInserted, notifyItemRemoved */
            }
        });

        /* DUMMY ACTION: for data update progress bar tied to RecyclerView */
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
        /* end dummy action */

        /* update home location button/display; not recyclerview */
        temperatureViewModel.getLocationData().observe(this, new Observer<List<MonitorLocation>>(){
            @Override
            public void onChanged(@Nullable List<MonitorLocation> monitorLocations) {
                List<MonitorLocation> tempList = temperatureViewModel.getLocationData().getValue();
                Toast.makeText(MainActivity.this, "onChanged: location", Toast.LENGTH_SHORT).show();

                /* relies on having home location at index 0, alternative location at index 1.
                * alternative is to have a drop-down list of unique locations added upon user prompt.
                * this could be done by scanning entire weather data list, extracting unique locations,
                * and offering the user a list of them - selecting one would change the data point
                * display to a trend of just that location.
                * this would mean a drop-down list view also depending directly on weather data change,
                * albeit it would depend on the location data extracted from each weather data point.
                *
                * but the current (home) location should only ever be one, and it should be
                * the only one displayed. */

                String localizedHomeName = tempList.get(0).getLocalizedName();
                Log.d(TAG, "Data observed from LocationDatabase. \nHome: "
                        +localizedHomeName+"; primary key: " + tempList.get(0).getId());
                Log.d(TAG, "Current location list size: "+ tempList.size());
                homeLocation.setText(localizedHomeName);
                /* use notifyItemInserted, notifyItemRemoved, notifyDataSetChanged ?? adapter methods */

            }

        });

        /* set up onClick for location buttons; should run GPS just for the location tied to the button */
        homeLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: fetch current location at user prompt.");
                temperatureViewModel.updateLocationOnPrompt();
            }
        });


        Log.d(TAG, "onCreate: started.");
    }

    private void showProgressBar() { progressBar.setVisibility(View.VISIBLE);}
    private void hideProgressBar() { progressBar.setVisibility(View.INVISIBLE);}


    /* GPS permissions */
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "GPS PERMISSION GRANTED BY USER. App behaviour is normal. ");
                } else {
                    Log.d(TAG, "GPS PERMISSION DENIED BY USER. Explain consequences and handle display.");
                }
            });
}
package com.example.monitor;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.monitor.adapters.RecyclerTemperatureAdapter;
import com.example.monitor.adapters.RecyclerWeatherAdapter;
import com.example.monitor.models.Temperature;
import com.example.monitor.models.Weather;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
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

    /* dummy action button for updates to the list */
    private FloatingActionButton dummyFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* initiate display elements */
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        /* initialize recycler view */
        /* alternative_adapter = new RecyclerAdapter(pass entries, pass this context); */
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
        recyclerView.setAdapter(weatherAdapter);

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
                Toast.makeText(MainActivity.this, "onChanged", Toast.LENGTH_SHORT).show();
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

        Log.d(TAG, "onCreate: started.");
    }

    private void showProgressBar() { progressBar.setVisibility(View.VISIBLE);}
    private void hideProgressBar() { progressBar.setVisibility(View.INVISIBLE);}
}
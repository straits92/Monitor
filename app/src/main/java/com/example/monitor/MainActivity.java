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

import com.example.monitor.adapters.RecyclerTemperatureAdapter;
import com.example.monitor.models.Temperature;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

/* to-do list
 * handle object dependencies via dependency injection
 * implement a data cache queried by repository
 * determine where data for the app is actually preserved
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
    private RecyclerTemperatureAdapter temperatureAdapter;

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

        /* dummy action for simulating async addition of new data to LiveData */
        dummyFab = findViewById(R.id.floatingActionButton);
        dummyFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                temperatureViewModel.addNewValue(new Temperature("20", "http://...", "10"));
            }
        });
        hideProgressBar();

        /* instantiate and set observation of the viewmodel; activity does not see lower modules */
        temperatureViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        temperatureViewModel.init();
        temperatureViewModel.getTempDataEntries().observe(this, new Observer<ArrayList<Temperature>>() {
            @Override /* should below be nullable? */
            public void onChanged(@Nullable  ArrayList<Temperature> temperatures) {
                temperatureAdapter.notifyDataSetChanged();
            }
        });

        /* for data update progress bar */
        temperatureViewModel.getIsUpdating().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (aBoolean) {
                    showProgressBar();
                } else {
                    hideProgressBar();
                    recyclerView.smoothScrollToPosition(temperatureViewModel
                            .getTempDataEntries().getValue().size() - 1);
                }
            }
        });

        initRecyclerView();

        Log.d(TAG, "onCreate: started.");
    }

    private void initRecyclerView() {
        Log.d(TAG, "dummyInitRecyclerView: populate with placeholders");

        /* classes should not be responsible for creating their own dependencies;
         * dependencies should be passed to an object instance from the outside */
        temperatureAdapter = new RecyclerTemperatureAdapter(temperatureViewModel
                .getTempDataEntries().getValue(), this );
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        recyclerView.setAdapter(temperatureAdapter);
    }

    private void showProgressBar() { progressBar.setVisibility(View.VISIBLE);}
    private void hideProgressBar() { progressBar.setVisibility(View.INVISIBLE);}

}
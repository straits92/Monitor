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
import android.graphics.Color;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
/* Monitor App: intended to fetch weather data from a weather station API,
* fetch temperature (and potentially other) data from a home temperature
* sensor governed by a Raspberry Pi set-up, and to display / compare these
* two sets of data on graphs. The data should be fetched on an hourly basis.
*  */

/* to-do list
 * handle object dependencies via dependency injection:
 *      (not implemented: used to reduce boilerplate code; does not preserve state.)
 *
 * implement a data cache queried by repository (done by Room functionality)
 *
 * implement network utilities for the web server data source (done)
 *
 * implement background hourly updates of the data (done)
 *
 * implement graphing display for data (done)
 *
 * allow for switching and resizing of display, showing/hiding different trends, scrolling the graph,
 *
 * improve graphic style
 *
 * query the local raspberry pi for temperature sensor data, store this data
 *
 * future extensions:
 * offer trend extrapolation
 * calculate heat loss of apartment
 * humidity, other parameters
 *
 * */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final Integer TEMPERATURE_SENSOR = 2;
    private static final Integer SINGLE_HOUR_DATA = 1;
    private static final Integer TWELVE_HOURS_DATA = 0;

    /* declare app modules */
    private MainActivityViewModel temperatureViewModel;

    /* declare display elements here */
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Button homeLocation;
    private LineChart temperatureLineChart;

    /* dummy action button for updates to the list */
//    private FloatingActionButton dummyFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* initiate display elements */
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        homeLocation = findViewById(R.id.homeLocation); /* should have an onClick too */

        temperatureLineChart = (LineChart) findViewById(R.id.idTemperatureLineChart1);

        /* initialize recycler view showing the 12h forecast */
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
        recyclerView.setAdapter(weatherAdapter);

        /* permission granted slowly; everything is instantiated before the user can approves.
        * so maybe gps activity should only be triggered once the user permits. */
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);

        /* user interaction via fab click goes here */
//        dummyFab = findViewById(R.id.floatingActionButton);
//        dummyFab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                showProgressBar();
//                /* calls to other code go here */
//            }
//        });
//        hideProgressBar();


        /* initialize ViewModel scoped to lifecycle of this activity; android will destroy it at end */
        temperatureViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* observe() is a livedata method, activity gets passed to it. code in onChanged should
        * redraw everything when any data is changed in the LiveData object.  */
        temperatureViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
//                Log.d(TAG, "onChanged: Data observed from WeatherRepository, added to RecyclerView, graph to be redrawn.");
                weatherAdapter.setWeatherRecyclerEntries(weathers); /* use notifyItemInserted, notifyItemRemoved */

                /* scan weathers to extract distinct data types; may be better as a background task for remoteModel? */
                List<Entry> twelveHourWeatherList = new ArrayList<>();
                List<Entry> hourlyWeatherList = new ArrayList<>()/*temperatureViewModel.getHourlyWeatherList()*/;
                List<Entry> sensorWeatherList = new ArrayList<>()/*temperatureViewModel.getSensorWeatherList()*/;

                /* this could be doable in the background too, but dailyTimeOrigin needs to be a
                * consistent value for the entire day, and computing it here is more taxing on the
                * UI thread as well as more accurate. */
                Calendar today = Calendar.getInstance();
                today.set(Calendar.MILLISECOND, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.HOUR_OF_DAY, 0);
                long dailyTimeOrigin = today.getTimeInMillis();

                /* should only collect data with persistence of 0, "visible" */
                separateWeatherDataTrends(dailyTimeOrigin, weathers, twelveHourWeatherList, hourlyWeatherList, sensorWeatherList);

                /* needed to avoid NegativeArraySizeException with MPAndroidChart library */
                Collections.sort(twelveHourWeatherList, new EntryXComparator());
                Collections.sort(hourlyWeatherList, new EntryXComparator());
                Collections.sort(sensorWeatherList, new EntryXComparator());

                /* bind the data to the temperatureLineChart */
                ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();
                LineDataSet twelveHourTemperatureSet = new LineDataSet(twelveHourWeatherList, "Forecast(12hr)");
                LineDataSet hourlyTemperatureSet = new LineDataSet(hourlyWeatherList, "Current(1hr)");
                LineDataSet sensorTemperatureSet = new LineDataSet(sensorWeatherList, "Sensor temperature");

                twelveHourTemperatureSet.setDrawCircles(true);twelveHourTemperatureSet.setColor(Color.BLACK);
                hourlyTemperatureSet.setDrawCircles(true); hourlyTemperatureSet.setColor(Color.GREEN);
                sensorTemperatureSet.setDrawCircles(true); sensorTemperatureSet.setColor(Color.RED);

                lineDataSets.add(twelveHourTemperatureSet);
                lineDataSets.add(hourlyTemperatureSet);
                lineDataSets.add(sensorTemperatureSet);

                /* set the value formatter to display the X axis as desired */
                ValueFormatter xAxisFormatter = new HourAxisValueFormatter(dailyTimeOrigin);
                XAxis xAxis = temperatureLineChart.getXAxis();
                xAxis.setValueFormatter(xAxisFormatter);

                temperatureLineChart.setData(new LineData(lineDataSets)); // does it also need axis annotation?

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
                Log.d(TAG, "Data observed from LocationDatabase. Location: "+localizedHomeName+"; location list size: "+tempList.size()
                        /*+"; primary key: " + tempList.get(0).getId()*/);
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

    /* graphing utilities
    * current graph issues:
    * x-axis labels are sporadic hh:mm even though the data hours are round values
    * no clear grid gradations for axes
    *
    *  */
    private void separateWeatherDataTrends(long dailyTimeOrigin, List<Weather> weathers, List<Entry> twelveHourWeatherList,
                                           List<Entry> hourlyWeatherList, List<Entry> sensorWeatherList) {
        Log.d(TAG, "separateWeatherDataTrends: entries to be generated for each data point, all trends");
        Iterator iter = weathers.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();

            /* if data isn't younger than 24h, don't show it */
            if (weatherEntryInIter.getPersistence() != 0)
                continue;

            Integer category = weatherEntryInIter.getCategory();
            float temperature = Float.parseFloat(weatherEntryInIter.getCelsius());
            long convertedDataMillis = 0;

            long dataPointMillis = weatherEntryInIter.getTimeInMillis();

            /* need to recalculate whenever we draw, the reference timestamp should be start of today */
            convertedDataMillis = dataPointMillis - dailyTimeOrigin;

            /* treat your Date as a timestamp when creating Entry object */
            Entry dataPoint = new Entry(convertedDataMillis, temperature);
            if (category == SINGLE_HOUR_DATA) {
                hourlyWeatherList.add(dataPoint);
            } else if (category == TWELVE_HOURS_DATA) {
                twelveHourWeatherList.add(dataPoint);
            } else if (category == TEMPERATURE_SENSOR) {
                sensorWeatherList.add(dataPoint);
            } else {
                continue;
            }

        }

    }

    /*in your IAxisValueFormatter is to convert the timestamp representation back to a Date and then
    * into something human readable*/
    public class HourAxisValueFormatter extends ValueFormatter {

        private long referenceTimestamp; // minimum timestamp in your data set
        private DateFormat mDataFormat;
        private Date mDate;

        public HourAxisValueFormatter(long referenceTimestamp) {
            this.referenceTimestamp = referenceTimestamp;
            this.mDataFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
            this.mDate = new Date();
        }

        /**
         * Called when a value from an axis is to be formatted
         * before being drawn. For performance reasons, avoid excessive calculations
         * and memory allocations inside this method.
         *
         * @param value the value to be formatted
//         * @param axis  the axis the value belongs to
         * @return
         */
        @Override
        public String getFormattedValue(float value/* AxisBase axis argument deprecated */) {
            // convertedTimestamp = originalTimestamp - referenceTimestamp
            long convertedTimestamp = (long) value;

            // Retrieve original timestamp
            long originalTimestamp = referenceTimestamp + convertedTimestamp;

            // Convert timestamp to hour:minute
            return getHour(originalTimestamp);
        }

//        @Override not present in superclass
//        public int getDecimalDigits() {
//            return 0;
//        }

        private String getHour(long timestamp){
            try{
                mDate.setTime(timestamp/* *1000, arguments already in millis */);
                return mDataFormat.format(mDate);
            }
            catch(Exception ex){
                return "xx";
            }
        }
    }

    /* helpers for the progress bar */
    private void showProgressBar() { progressBar.setVisibility(View.VISIBLE);}
    private void hideProgressBar() { progressBar.setVisibility(View.INVISIBLE);}

    /* GPS permissions */
    private ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Log.d(TAG, "GPS PERMISSION GRANTED BY USER. App behaviour is normal. ");
                /* maybe: trigger an update of location and then of the entire current
                * default list once it turns out the permission is granted */
//                temperatureViewModel.updateLocationOnPrompt();
//                temperatureViewModel.updateWeatherDataOnPrompt();
            } else {
                Log.d(TAG, "GPS PERMISSION DENIED BY USER. Explain consequences and handle display.");
            }
        });
}
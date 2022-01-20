/* Monitor App
 * Intended to fetch weather data from a weather station API, fetch temperature (and potentially
 * other) data from a home temperature sensor governed by a Raspberry Pi set-up, and to display /
 * compare these sets of data on graphs.
 */
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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.monitor.adapters.RecyclerWeatherAdapter;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /* enumerated constants for data type and recency */
    private static final Integer TEMPERATURE_SENSOR = 2;
    private static final Integer SINGLE_HOUR_DATA = 1;
    private static final Integer TWELVE_HOURS_DATA = 0;
    private static final Integer UNDER_48H = 0;

    /* declare app modules */
    private MainActivityViewModel temperatureViewModel;

    /* declare display elements here */
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Button homeLocation;
    private Button sensorQuery;
    private TextView sensorQueryOutput;
    private LineChart temperatureLineChart;
    private AutoCompleteTextView dropDownListParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* set up dropdown list based on hardcoded parameters in ~/res/values/strings */
        String[] monitorParameters = getResources().getStringArray(R.array.monitoring_parameters);
        ArrayAdapter dropDownListParametersAdapter = new ArrayAdapter(this,
                R.layout.dropdown_item_monitoring_parameter, monitorParameters);
        dropDownListParams = findViewById(R.id.autoCompleteTextView);
        dropDownListParams.setAdapter(dropDownListParametersAdapter);

        /* initiate display elements */
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        homeLocation = findViewById(R.id.homeLocation); /* should have an onClick too */
        temperatureLineChart = (LineChart) findViewById(R.id.idTemperatureLineChart1);
        sensorQuery = findViewById(R.id.getSensorReading);
        sensorQueryOutput = findViewById(R.id.instantSensorReading);

        /* initialize recycler view used for debugging */
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
        recyclerView.setAdapter(weatherAdapter);

        /* permission granted slowly; everything is instantiated before the user can approve.*/
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);

        /* initialize ViewModel scoped to lifecycle of this activity; android will destroy it at end */
        temperatureViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* observe() is a LiveData callback. When LiveData changes, redraw. */
        temperatureViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
//                Log.d(TAG, "onChanged: Data observed from WeatherRepository, added to " +
//                        "RecyclerView, graph to be redrawn.");
                weatherAdapter.setWeatherRecyclerEntries(weathers);
                /* use notifyItemInserted, notifyItemRemoved */

                /* create fixed chart: 0 to 48 hours (yesterday and today), +40, -20 degrees C */
                XAxis xAxis = temperatureLineChart.getXAxis();
                xAxis.setLabelCount(8);
                xAxis.setAxisMaximum(48);
                xAxis.setAxisMinimum(0);
//                xAxis.setGranularityEnabled(true);
//                xAxis.setGranularity(48/4);

                YAxis yAxisLeft = temperatureLineChart.getAxisLeft();
                yAxisLeft.setAxisMaximum(40);
                yAxisLeft.setAxisMinimum(-20);
                yAxisLeft.setGranularityEnabled(true);
                yAxisLeft.setGranularity(60/12);

                YAxis yAxisRight = temperatureLineChart.getAxisRight();
                yAxisRight.setAxisMaximum(40);
                yAxisRight.setAxisMinimum(-20);
                yAxisRight.setGranularityEnabled(true);
                yAxisRight.setGranularity(60/12);

                /* get start of today in this timezone */
                Calendar today = Calendar.getInstance();
                today.set(Calendar.MILLISECOND, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.HOUR_OF_DAY, 0);
                long dailyTimeOrigin = today.getTimeInMillis();

                List<Entry> twelveHourWeatherList = new ArrayList<>();
                List<Entry> hourlyWeatherList = new ArrayList<>();
                List<Entry> sensorWeatherList = new ArrayList<>();
                /*temperatureViewModel.getSensorWeatherList()*/

                separateWeatherDataTrendsFixed(dailyTimeOrigin, weathers, twelveHourWeatherList,
                        hourlyWeatherList, sensorWeatherList);

                /* sorting needed to avoid NegativeArraySizeException with MPAndroidChart library */
                Collections.sort(twelveHourWeatherList, new EntryXComparator());
                Collections.sort(hourlyWeatherList, new EntryXComparator());
                Collections.sort(sensorWeatherList, new EntryXComparator());

                /* bind the data to the temperatureLineChart */
                ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();
                LineDataSet twelveHourTemperatureSet = new LineDataSet(twelveHourWeatherList,
                        "Forecast(12hr)");
                LineDataSet hourlyTemperatureSet = new LineDataSet(hourlyWeatherList,
                        "Current(1hr)");
                LineDataSet sensorTemperatureSet = new LineDataSet(sensorWeatherList,
                        "Sensor temperature");

                twelveHourTemperatureSet.setDrawCircles(true);twelveHourTemperatureSet.setColor(Color.BLACK);
                hourlyTemperatureSet.setDrawCircles(true); hourlyTemperatureSet.setColor(Color.YELLOW);
                sensorTemperatureSet.setDrawCircles(true); sensorTemperatureSet.setColor(Color.GREEN);

                lineDataSets.add(twelveHourTemperatureSet);
                lineDataSets.add(hourlyTemperatureSet);
                lineDataSets.add(sensorTemperatureSet);

                temperatureLineChart.setData(new LineData(lineDataSets));

//                /* set the value formatter to display the X axis as desired */
//                ValueFormatter xAxisFormatter = new HourAxisValueFormatter(dailyTimeOrigin);
//                XAxis xAxis = temperatureLineChart.getXAxis();
//                xAxis.setValueFormatter(xAxisFormatter);
            }
        });

        /* update home location button/display; not recyclerview */
        temperatureViewModel.getLocationData().observe(this,
                new Observer<List<MonitorLocation>>(){
            @Override
            public void onChanged(@Nullable List<MonitorLocation> monitorLocations) {
                List<MonitorLocation> tempList = temperatureViewModel.getLocationData().getValue();
                String localizedHomeName = tempList.get(0).getLocalizedName();
                Log.d(TAG, "Data observed from LocationDatabase. Location: "
                        +localizedHomeName+"; location list size: "+tempList.size());
                homeLocation.setText(localizedHomeName);
                /* use notifyItemInserted, notifyItemRemoved, notifyDataSetChanged adapter methods */
            }
        });

        /* set up onClick for location buttons; run GPS just for the location tied to the button */
        homeLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: fetch current location at user prompt.");
                temperatureViewModel.updateLocationOnPrompt();
            }
        });

        sensorQuery.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: fetch latest sensor reading at user prompt.");
                temperatureViewModel.updateSensorReadingOnPrompt();

                // get the value from the execution model; time of sensor reading, and value

                // bind it to the sensor reading display
//                sensorQueryOutput.setText();
            }
        });

        Log.d(TAG, "onCreate: started.");

        /* DUMMY ACTION: for data update progress bar tied to RecyclerView */
//        temperatureViewModel.getIsUpdating().observe(this, new Observer<Boolean>() {
//            @Override
//            public void onChanged(Boolean aBoolean) {
//                if (aBoolean) {
//                    showProgressBar();
//                } else {
//                    hideProgressBar();
//                    recyclerView.smoothScrollToPosition(0);/* first element in list, last inserted */
//                }
//            }
//        });
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
    }

    /* graphing utilities */
    private void separateWeatherDataTrendsFixed(long dailyTimeOrigin, List<Weather> weathers,
                                                List<Entry> twelveHourWeatherList,
                                                List<Entry> hourlyWeatherList,
                                                List<Entry> sensorWeatherList) {
        long startOfYesterday = dailyTimeOrigin - 86400000; // a day in millis is 60*60*24*1000
        String currentSelectedLocation = (String) homeLocation.getText();
//        Log.d(TAG, "separateWeatherDataTrendsFixed: entries to be generated for each data " +
//                "point, all trends, location: "+currentSelectedLocation);

        Iterator iter = weathers.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();

            /* if data isn't younger than 48h, don't show it */
            if (weatherEntryInIter.getPersistence() != UNDER_48H)
                continue;

            /* if data does not match current selected location as appears on button, don't show it */
            if (!(weatherEntryInIter.getLocation().equals(currentSelectedLocation))) {
                continue;
            }

            /* get hours offset from start of yesterday, get temperature */
            long dataPointTime = weatherEntryInIter.getTimeInMillis();
            long hour = (dataPointTime - startOfYesterday)/3600000; // 0 to 48
            float temperature = Float.parseFloat(weatherEntryInIter.getCelsius());
            Entry dataPoint = new Entry(hour, temperature);

            /* need to recalculate whenever we draw, the reference timestamp should be start of today */
//            convertedDataMillis = dataPointMillis - dailyTimeOrigin;

            Integer category = weatherEntryInIter.getCategory();
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
                Log.d(TAG, "GPS PERMISSION DENIED BY USER. Explain consequences, " +
                        "handle display.");
            }
        });

    /* convert the timestamp representation back to a Date and then into something human readable */
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

}
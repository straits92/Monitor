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
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.monitor.adapters.RecyclerWeatherAdapter;
import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;
import com.hivemq.client.internal.mqtt.message.MqttMessage;

import java.io.UnsupportedEncodingException;
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
    private static final Integer HOME_SENSOR = 2;
    private static final Integer SINGLE_HOUR_DATA = 1;
    private static final Integer TWELVE_HOURS_DATA = 0;
    private static final Integer UNDER_48H = 0;

    private static final Integer TEMPERATURE = 0;
    private static final Integer HUMIDITY = 1;

    /* declare app modules */
    private MainActivityViewModel weatherViewModel;

    /* declare display elements here */
    private RecyclerView recyclerView;
    private Button homeLocation;
    private Button sensorQuery;
    private Button navigateToDevices;
    private TextView sensorQueryOutput;
    private TextView sensorQueryTimestamp;
    private LineChart weatherLineChart;
    private AutoCompleteTextView dropDownListParams;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* set up dropdown list based on hardcoded parameters in ~/res/values/strings */
        String[] monitorParameters = getResources().getStringArray(R.array.monitoring_parameters);
        ArrayAdapter dropDownListParametersAdapter = new ArrayAdapter(this,
                R.layout.dropdown_item_monitoring_parameter, monitorParameters);
        dropDownListParams = findViewById(R.id.dropDownParametersText);
        dropDownListParams.setAdapter(dropDownListParametersAdapter);

        /* initiate display elements */
        recyclerView = findViewById(R.id.recyclerView);
        homeLocation = findViewById(R.id.homeLocation);
        weatherLineChart = (LineChart) findViewById(R.id.idTemperatureLineChart1);
        sensorQuery = findViewById(R.id.getSensorReading);
        sensorQueryOutput = findViewById(R.id.instantSensorReading);
        sensorQueryTimestamp = findViewById(R.id.sensorReadingTimestamp);
        sensorQueryOutput.setText("N/A");
        sensorQueryTimestamp.setText("N/A");
        navigateToDevices = findViewById(R.id.idNavigateToDevices);

        /* initialize recycler view used for debugging */
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
        recyclerView.setAdapter(weatherAdapter);

        /* permission granted slowly; everything is instantiated before the user can approve.*/
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);

        /* initialize ViewModel scoped to lifecycle of mainactivity; android to destroy it at end */
        weatherViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* test publish and test subscription for mqtt from this activity  */
        MQTTConnection.publishBlocking("MainActivity_MonitorApp_test", "general");
        MQTTConnection.subscribeBlocking("sensors/json");

        /* switch to device control panels, without creating the activity+viewmodel anew */
        navigateToDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent openDeviceActivity = new Intent(MainActivity.this, DeviceActivity.class);
                openDeviceActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivityIfNeeded(openDeviceActivity, 0);
            }
        });

        /* observers */
        /* observe() is a LiveData callback to weather data. When the data changes, redraw. */
        weatherViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
                weatherAdapter.setWeatherRecyclerEntries(weathers);/* use notifyItemInserted, etc */
                redrawGraph(weathers);
            }
        });

        /* update home location button/display; potentially, a location change should also prompt
        * an immediate request for a 12hr forecast. (not implemented) */
        weatherViewModel.getLocationData().observe(this,
                new Observer<List<MonitorLocation>>(){
            @Override
            public void onChanged(@Nullable List<MonitorLocation> monitorLocations) {
                List<MonitorLocation> tempList = weatherViewModel.getLocationData().getValue();
                String localizedHomeName = tempList.get(0).getLocalizedName();
                Log.d(TAG, "Data observed from LocationDatabase. Location: "
                        +localizedHomeName+"; location list size: "+tempList.size());
                homeLocation.setText(localizedHomeName);
                /* use notifyItemInserted, notifyItemRemoved, notifyDataSetChanged adapter methods */

                /* request the execution model to fetch relevant data from the database */
                List<Weather> weathers = weatherViewModel.getWeatherDataEntriesFromDb();
                if (weathers == null) {
                    Log.i(TAG, "onItemClick: requested parameter not in database.");
                    return;
                }
                redrawGraph(weathers);
            }
        });

        /* for obtaining instantaneous sensor reading upon user prompt */
        weatherViewModel.getInstantSensorReading().observe(this,
            new Observer<String>(){
                @Override
                public void onChanged(@Nullable String s) {
                    String valueSubstring = s.substring(1,5);
                    String timestampSubstring = s.substring(10, 18);
                    sensorQueryOutput.setText(valueSubstring);
                    sensorQueryTimestamp.setText(timestampSubstring);
                }
        });

        /* onClick listeners */
        /* sets up instantaneous sensor reading */
        sensorQuery.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                weatherViewModel.updateSensorReadingOnPrompt();
            }
        });

        /* set up onClick for location buttons; run GPS just for the location tied to the button */
        homeLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: fetch current location at user prompt.");
                weatherViewModel.updateLocationOnPrompt();
            }
        });

        /* sets up the listener for changes to the drop down selection */
        dropDownListParams.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                /* request the execution model to fetch relevant data from the database */
                List<Weather> weathers = weatherViewModel.getWeatherDataEntriesFromDb();
                if (weathers == null) {
                    Log.i(TAG, "onItemClick: requested parameter not in database.");
                    return;
                }
                redrawGraph(weathers);
            }
        });

    }

    /* graphing utilities */
    private void redrawGraph(List<Weather> weathers) {
        String selectedParameter = dropDownListParams.getText().toString();
        Integer selectedParam = 0;
        long dailyTimeOrigin = getStartOfTodayMillis();
//        Log.d(TAG, "onClick: user selected the following option in drop-down menu: "
//                + selectedParameter);

        if (selectedParameter.equals("Temperature")) {
            selectedParam = TEMPERATURE;
        } else if (selectedParameter.equals("Humidity")) {
            selectedParam = HUMIDITY;
        } else {
//            Log.i(TAG, "OnClickListener: no valid data selected from drop-down list");
        }

        /* draw the obtained data on the display */
        bindDataToGraph(dailyTimeOrigin, weathers, selectedParam);

    }

    private void bindDataToGraph(long dailyTimeOrigin, List<Weather> weathers, Integer selectedParam) {
        /* create fixed chart: x-axis is 0 to 48 hours (yesterday and today) */
        if (selectedParam == TEMPERATURE) {
            /* y axis is +40, -20 degrees C */
            drawChartAxes(-20, 40, 12, 12);
            weatherLineChart.getDescription()
                    .setText("Temperature in Celsius (yesterday, today)");           
        } else if (selectedParam == HUMIDITY) {
            /* y axis is 0 to 100% humidity */
            drawChartAxes(0, 100, 12, 12);
            weatherLineChart.getDescription()
                    .setText("Humidity in % (yesterday, today)");
        } else {
            Log.i(TAG, "bindDataToGraph: no recognized data provided, return without drawing.");
            return;
        }

        /* set up data to be drawn on the graph */
        List<Entry> twelveHourWeatherList = new ArrayList<>();
        List<Entry> hourlyWeatherList = new ArrayList<>();
        List<Entry> sensorWeatherList = new ArrayList<>();
        /*temperatureViewModel.getSensorWeatherList()*/

        separateWeatherDataTrendsFixed(dailyTimeOrigin, weathers, twelveHourWeatherList,
                hourlyWeatherList, sensorWeatherList, selectedParam);

        /* sorting needed to avoid NegativeArraySizeException with MPAndroidChart library */
        Collections.sort(twelveHourWeatherList, new EntryXComparator());
        Collections.sort(hourlyWeatherList, new EntryXComparator());
        Collections.sort(sensorWeatherList, new EntryXComparator());

        /* bind the data to the temperatureLineChart */
        ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();
        LineDataSet twelveHourTemperatureSet = new LineDataSet(twelveHourWeatherList,
                "API (12hr)");
        LineDataSet hourlyTemperatureSet = new LineDataSet(hourlyWeatherList,
                "API (1hr)");
        LineDataSet sensorTemperatureSet = new LineDataSet(sensorWeatherList,
                "Sensor (1hr)");

        twelveHourTemperatureSet.setDrawCircles(true);twelveHourTemperatureSet.setColor(Color.RED);
        hourlyTemperatureSet.setDrawCircles(true); hourlyTemperatureSet.setColor(Color.GREEN);
        sensorTemperatureSet.setDrawCircles(true); sensorTemperatureSet.setColor(Color.BLUE);

        lineDataSets.add(twelveHourTemperatureSet);
        lineDataSets.add(hourlyTemperatureSet);
        lineDataSets.add(sensorTemperatureSet);

        weatherLineChart.setData(new LineData(lineDataSets));
    }

    private void separateWeatherDataTrendsFixed(long dailyTimeOrigin, List<Weather> weathers,
                                                List<Entry> twelveHourWeatherList,
                                                List<Entry> hourlyWeatherList,
                                                List<Entry> sensorWeatherList,
                                                Integer selectedParam) {
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

            float parameter;
            if (selectedParam == TEMPERATURE) {
                parameter = Float.parseFloat(weatherEntryInIter.getCelsius());
            } else if (selectedParam == HUMIDITY) {
                parameter = Float.parseFloat(weatherEntryInIter.getHumidity());
            } else {
                parameter = 0.01f;
            }
            Entry dataPoint = new Entry(hour, parameter);

            /* recalculate whenever drawn, the reference timestamp should be start of today */
//            convertedDataMillis = dataPointMillis - dailyTimeOrigin;

            Integer category = weatherEntryInIter.getCategory();
            if (category == SINGLE_HOUR_DATA) {
                hourlyWeatherList.add(dataPoint);
            } else if (category == TWELVE_HOURS_DATA) {
                twelveHourWeatherList.add(dataPoint);
            } else if (category == HOME_SENSOR) {
                sensorWeatherList.add(dataPoint);
            } else {
                continue;
            }
        }

    }

    private long getStartOfTodayMillis() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.HOUR_OF_DAY, 0);
        long dailyTimeOrigin = today.getTimeInMillis();
        return dailyTimeOrigin;
    }

    private void drawChartAxes(Integer yAxisMin, Integer yAxisMax, Integer yLabelCount, Integer xLabelCount) {
        XAxis xAxis = weatherLineChart.getXAxis();
        xAxis.setAxisMaximum(48);
        xAxis.setAxisMinimum(0);
//        xAxis.setGranularityEnabled(true);
//        xAxis.setGranularity(48/4); /* or xAxis.setLabelCount(n) instead*/
        xAxis.setLabelCount(xLabelCount);

        YAxis yAxisLeft = weatherLineChart.getAxisLeft();
        yAxisLeft.setAxisMaximum(yAxisMax);
        yAxisLeft.setAxisMinimum(yAxisMin);
        yAxisLeft.setLabelCount(yLabelCount);

        YAxis yAxisRight = weatherLineChart.getAxisRight();
        yAxisRight.setAxisMaximum(yAxisMax);
        yAxisRight.setAxisMinimum(yAxisMin);
        yAxisRight.setLabelCount(yLabelCount);

        return;
    }

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

        private String getHour(long timestamp){
            try{
                mDate.setTime(timestamp/* *1000, arguments already in millis */);
                return mDataFormat.format(mDate);
            }
            catch(Exception ex){
                return "xx";
            }
        }
//        @Override not present in superclass
//        public int getDecimalDigits() {
//            return 0;
//        }

//        /* set the value formatter to display the X axis, in a data observer */
//        ValueFormatter xAxisFormatter = new HourAxisValueFormatter(dailyTimeOrigin);
//        XAxis xAxis = temperatureLineChart.getXAxis();
//        xAxis.setValueFormatter(xAxisFormatter);
    }

}
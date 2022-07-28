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
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.example.monitor.models.MonitorLocation;
import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.repositories.networkutils.TopicData;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    /* declare app modules */
    private MainActivityViewModel weatherViewModel;

    /* declare display elements here */
//    private RecyclerView recyclerView; // for debugging
    private Button homeLocation;
    private Button sensorQuery;
    private Button navigateToDevices;
    private Switch weather12hrSwitch;
    private Switch weather1hrSwitch;
    private Switch sensor1hrSwitch;
    private TextView sensorQueryOutput;
    private TextView sensorQueryTimestamp;
    private TextView sensorQueryOutputTitle;
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
        homeLocation = findViewById(R.id.homeLocation);
        weatherLineChart = (LineChart) findViewById(R.id.idTemperatureLineChart1);
        sensorQuery = findViewById(R.id.getSensorReading);
        sensorQueryOutput = findViewById(R.id.instantSensorReading);
        sensorQueryTimestamp = findViewById(R.id.sensorReadingTimestamp);
        sensorQueryOutputTitle = findViewById(R.id.sensorReadingHeader2);
        navigateToDevices = findViewById(R.id.idNavigateToDevices);
        weather12hrSwitch = findViewById(R.id.switch1);
        weather1hrSwitch = findViewById(R.id.switch2);
        sensor1hrSwitch = findViewById(R.id.switch3);


        /* initialize recycler view used for debugging */
//        recyclerView = findViewById(R.id.recyclerView);
//        LinearLayoutManager llm = new LinearLayoutManager(this);
//        recyclerView.setLayoutManager(llm); /* necessary for forming the recyclerview */
//        RecyclerWeatherAdapter weatherAdapter = new RecyclerWeatherAdapter();
//        recyclerView.setAdapter(weatherAdapter);

        /* permission granted slowly; everything is instantiated before the user can approve.*/
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);

        /* initialize ViewModel scoped to lifecycle of mainactivity; android to destroy it at end */
        weatherViewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        /* test publish and test subscription for mqtt from this activity  */
        MQTTConnection.publishBlocking(TAG+"_MonitorApp_test", TopicData.getGeneralTopic());

        /*** LiveData observers ***/
        /* observe() is a LiveData callback to weather data. When the data changes, redraw. */
        weatherViewModel.getWeatherDataEntries().observe(this, new Observer<List<Weather>>(){
            @Override
            public void onChanged(@Nullable List<Weather> weathers) {
//                weatherAdapter.setWeatherRecyclerEntries(weathers); // for debugging
                redrawGraph(weathers);
            }
        });

        /* update home location button/display;
        * may prompt an immediate request for a 12hr forecast. (not implemented) */
        weatherViewModel.getLocationData().observe(this,
                new Observer<List<MonitorLocation>>(){
            @Override
            public void onChanged(@Nullable List<MonitorLocation> monitorLocations) {
                List<MonitorLocation> tempList = weatherViewModel.getLocationData().getValue();
                String localizedHomeName = tempList.get(0).getLocalizedName();
                Log.d(TAG, "Data observed from LocationDatabase. Location: "
                        +localizedHomeName+"; location list size: "+tempList.size());
                if(localizedHomeName.length() > 12) {
                    String shortened = localizedHomeName;
                    homeLocation.setText(shortened.substring(0, 10) + "...");
                } else {
                    homeLocation.setText(localizedHomeName);
                }

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
                    String firstCopy = new String(s);
                    String secondCopy = new String (s);
                    Integer index0 = firstCopy.indexOf("V");
                    Integer index1 = firstCopy.indexOf(";");
                    Integer index2 = secondCopy.indexOf("T");
                    Integer index3 = secondCopy.indexOf("|");

                    if (index0 < 0) {
                        sensorQueryOutput.setText("N/A");
                        sensorQueryTimestamp.setText("N/A");
                    } else {
                        String valueSubstring =  firstCopy.substring(index0 + 1, index1);
                        String timestampSubstring = secondCopy.substring(index2 + 1, index3);
                        sensorQueryOutput.setText(valueSubstring);
                        sensorQueryTimestamp.setText(timestampSubstring);
                    }

                }
        });

        /*** onClick listeners ***/
        /* switch to device control panels, without creating the activity+viewmodel anew */
        navigateToDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent openDeviceActivity = new Intent(MainActivity.this, DeviceActivity.class);
                openDeviceActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivityIfNeeded(openDeviceActivity, 0);
            }
        });

        /* sets up instantaneous sensor reading */
        sensorQuery.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {// should have parameter as argument
                String selectedParameter = dropDownListParams.getText().toString();
                weatherViewModel.updateSensorReadingOnPrompt(selectedParameter);
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

                /* change instantaneous sensor query display */
                String selectedParameter = dropDownListParams.getText().toString();
                sensorQueryOutputTitle.setText(selectedParameter);
                sensorQueryOutput.setText("N/A");
                sensorQueryTimestamp.setText("N/A");
            }
        });

        weather12hrSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    List<Weather> weathers = weatherViewModel.getWeatherDataEntriesFromDb();
                    redrawGraph(weathers);
            }
        });
        weather1hrSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                List<Weather> weathers = weatherViewModel.getWeatherDataEntriesFromDb();
                redrawGraph(weathers);
            }
        });
        sensor1hrSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                List<Weather> weathers = weatherViewModel.getWeatherDataEntriesFromDb();
                redrawGraph(weathers);
            }
        });
    }

    /*** graphing utilities ***/
    private void redrawGraph(List<Weather> weathers) {
        String selectedParameter = dropDownListParams.getText().toString();
        Integer selectedParam = 0;
        long dailyTimeOrigin = getStartOfTimeUnitMillis("day");

        if (selectedParameter.equals("Temperature")) {
            selectedParam = MonitorEnums.TEMPERATURE;
        } else if (selectedParameter.equals("Humidity")) {
            selectedParam = MonitorEnums.HUMIDITY;
        } else { // unhandled selection, brightness comes here later
        }

        /* draw the obtained data on the display */
        bindDataToGraph(dailyTimeOrigin, weathers, selectedParam);

    }

    private void bindDataToGraph(long dailyTimeOrigin, List<Weather> weathers, Integer selectedParam) {
        /* create fixed chart: x-axis is 0 to 48 hours (yesterday and today) */
        String graphText = "None selected";
        if (selectedParam == MonitorEnums.TEMPERATURE) {
            drawChartAxes(MonitorEnums.MINTEMP, MonitorEnums.MAXTEMP, 12, 12);
            graphText = "Temperature in Celsius (yesterday, today)";
        } else if (selectedParam == MonitorEnums.HUMIDITY) {
            drawChartAxes(0, 100, 12, 12);
            graphText = "Humidity in % (yesterday, today)";
        } else {
            Log.i(TAG, "bindDataToGraph: no recognized data provided.");
            return;
        }
        weatherLineChart.getDescription().setText(graphText);
        weatherLineChart.getDescription().setTextColor(Color.WHITE);
        weatherLineChart.getDescription().setTextSize(12f);

        /* set up data to be drawn on the graph */
        List<Entry> twelveHourWeatherList = new ArrayList<>();
        List<Entry> hourlyWeatherList = new ArrayList<>();
        List<Entry> sensorWeatherList = new ArrayList<>();

        separateWeatherDataTrendsFixed(dailyTimeOrigin, weathers, twelveHourWeatherList,
                hourlyWeatherList, sensorWeatherList, selectedParam);

        /* sorting needed to avoid NegativeArraySizeException with MPAndroidChart library */
        Collections.sort(twelveHourWeatherList, new EntryXComparator());
        Collections.sort(hourlyWeatherList, new EntryXComparator());
        Collections.sort(sensorWeatherList, new EntryXComparator());

        /* bind the data to the temperatureLineChart */
        ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();
        LineDataSet twelveHourDataSet = new LineDataSet(twelveHourWeatherList,
                "Weather API (12hr)");
        LineDataSet oneHourDataSet = new LineDataSet(hourlyWeatherList,
                "Weather API (1hr)");
        LineDataSet oneHourSensorDataSet = new LineDataSet(sensorWeatherList,
                "Sensor (1hr)");

        twelveHourDataSet.setDrawCircles(true);twelveHourDataSet.setColor(Color.RED);
        oneHourDataSet.setDrawCircles(true); oneHourDataSet.setColor(Color.GREEN);
        oneHourSensorDataSet.setDrawCircles(true); oneHourSensorDataSet.setColor(Color.YELLOW);

        if (weather12hrSwitch.isChecked()) {lineDataSets.add(twelveHourDataSet);}
        if (weather1hrSwitch.isChecked()) {lineDataSets.add(oneHourDataSet);}
        if (sensor1hrSwitch.isChecked()) {lineDataSets.add(oneHourSensorDataSet);}

        weatherLineChart.setData(new LineData(lineDataSets));
        weatherLineChart.getLegend().setTextColor(Color.WHITE);
    }

    private void separateWeatherDataTrendsFixed(long dailyTimeOrigin, List<Weather> weathers,
                                                List<Entry> twelveHourWeatherList,
                                                List<Entry> hourlyWeatherList,
                                                List<Entry> sensorWeatherList,
                                                Integer selectedParam) {
        long startOfYesterday = dailyTimeOrigin - 86400000; // a day in millis is 60*60*24*1000
        String currentSelectedLocation = (String) homeLocation.getText();

        Iterator iter = weathers.iterator();
        while (iter.hasNext()) {
            Weather weatherEntryInIter = (Weather) iter.next();

            /* if data isn't younger than 48h, don't show it */
            if (weatherEntryInIter.getPersistence() != MonitorEnums.UNDER_48H)
                continue;

            /* if data does not match current selected location as appears on button, don't show it */
            if (!(weatherEntryInIter.getLocation().equals(currentSelectedLocation))) {
                continue;
            }

            /* get hours offset from start of yesterday; recalculated whenever drawn */
            long dataPointTime = weatherEntryInIter.getTimeInMillis();
            long hour = (dataPointTime - startOfYesterday)/3600000; // 0 to 48

            float parameter;
            if (selectedParam == MonitorEnums.TEMPERATURE) {
                parameter = Float.parseFloat(weatherEntryInIter.getCelsius());
            } else if (selectedParam == MonitorEnums.HUMIDITY) {
                parameter = Float.parseFloat(weatherEntryInIter.getHumidity());
            } else {
                parameter = 0.01f;
            }

            Entry dataPoint = new Entry(hour, parameter);
            Integer category = weatherEntryInIter.getCategory();
            if (category == MonitorEnums.SINGLE_HOUR_DATA) {
                hourlyWeatherList.add(dataPoint);
            } else if (category == MonitorEnums.TWELVE_HOURS_DATA) {
                twelveHourWeatherList.add(dataPoint);
            } else if (category == MonitorEnums.HOME_SENSOR) {
                sensorWeatherList.add(dataPoint);
            } else {
                continue;
            }
        }

    }

    private long getStartOfTimeUnitMillis(String unit) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MINUTE, 0);
        if (unit.equals("day")){
            today.set(Calendar.HOUR_OF_DAY, 0);
        } else {}
        long timeUnitOrigin = today.getTimeInMillis();
        return timeUnitOrigin;
    }

    private void drawChartAxes(Integer yAxisMin, Integer yAxisMax, Integer yLabelCount, Integer xLabelCount) {
        XAxis xAxis = weatherLineChart.getXAxis();
        xAxis.setAxisMaximum(48);
        xAxis.setAxisMinimum(0);
        xAxis.setLabelCount(xLabelCount); // or enable granularity, and xAxis.setGranularity(48/4);
        xAxis.setTextColor(Color.WHITE); // getResources().getColor(R.color.yellowish_orange);

        YAxis yAxisLeft = weatherLineChart.getAxisLeft();
        yAxisLeft.setAxisMaximum(yAxisMax);
        yAxisLeft.setAxisMinimum(yAxisMin);
        yAxisLeft.setLabelCount(yLabelCount);
        yAxisLeft.setTextColor(Color.WHITE);

        YAxis yAxisRight = weatherLineChart.getAxisRight();
        yAxisRight.setAxisMaximum(yAxisMax);
        yAxisRight.setAxisMinimum(yAxisMin);
        yAxisRight.setLabelCount(yLabelCount);
        yAxisRight.setTextColor(Color.WHITE);

        /* draw line showing current time */
        long dailyTimeOrigin = getStartOfTimeUnitMillis("day");
        long currentTime = getStartOfTimeUnitMillis("hour");
        long startOfYesterday = dailyTimeOrigin - 86400000; // a day in millis is 60*60*24*1000
        long currentHour = (currentTime - startOfYesterday)/3600000;

        xAxis.removeAllLimitLines();
        LimitLine ll = new LimitLine(currentHour, "T: "+(currentHour%24)+"h");
        ll.setLineColor(Color.RED);
        ll.setLineWidth(2f);
        ll.setTextColor(Color.WHITE);
        ll.setTextSize(12f);
        ll.setLabelPosition(LimitLine.LimitLabelPosition.LEFT_TOP); // or change when close to xmax
        xAxis.addLimitLine(ll);
    }

    /*** GPS permissions ***/
    private ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Log.d(TAG, "GPS PERMISSION GRANTED BY USER. App behaviour is normal. ");
            } else {
                Log.d(TAG, "GPS PERMISSION DENIED BY USER. Default to Belgrade.");
            }
        });
}
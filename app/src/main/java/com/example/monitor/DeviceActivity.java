package com.example.monitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.example.monitor.models.Weather;
import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.github.mikephil.charting.charts.LineChart;

import java.util.List;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = "DeviceActivity|";
    private static Float MAX_LED_INTENSITY = 35.0f;
    private static Float MAX_SEEKBAR_VALUE = 100.0f;

    /* declare display elements here */
    private AutoCompleteTextView dropDownListDevices;
    private Button navigateToSensors;
    private Button hiddenOne;
    private SeekBar seekBar;
    private Switch toggleButton;
    private Button hiddenTwo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        MQTTConnection.publishBlocking("DeviceActivity_MonitorApp_test", "general");

        navigateToSensors = findViewById(R.id.idNavigateToSensors);

        hiddenOne = findViewById(R.id.dummyButtonForHideShow1);
        hiddenTwo = findViewById(R.id.dummyButtonForHideShow2);
        seekBar = findViewById(R.id.varyingOutputSeekBar);
        toggleButton = findViewById(R.id.toggleDeviceOnOff);

        hideElements();

        /* set up dropdown list based on hardcoded parameters in ~/res/values/strings */
        String[] monitorDevices = getResources().getStringArray(R.array.monitoring_devices);
        ArrayAdapter dropDownListDevicesAdapter = new ArrayAdapter(this,
                R.layout.dropdown_item_monitoring_parameter, monitorDevices); // xml for parameters reused here? or does it need its own?
        dropDownListDevices = findViewById(R.id.dropDownDevicesText);
        dropDownListDevices.setAdapter(dropDownListDevicesAdapter);

        navigateToSensors.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DeviceActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });


        /* sets up the listener for changes to the drop down selection */
        dropDownListDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String selectedParameter = dropDownListDevices.getText().toString();
                hideElements();

                if (selectedParameter.equals("LED Light")) {
                    seekBar.setVisibility(View.VISIBLE);
                    seekBar.setClickable(true);
                    toggleButton.setVisibility(View.VISIBLE);
                    toggleButton.setClickable(true);

                } else if (selectedParameter.equals("Hidden 1 (dummy)")) {
                    hiddenOne.setVisibility(View.VISIBLE);
                    hiddenOne.setClickable(true);
                } else if (selectedParameter.equals("Hidden 2 (dummy)")) {
                    hiddenTwo.setVisibility(View.VISIBLE);
                    hiddenTwo.setClickable(true);
                } else {}

            }
        });

        hiddenOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "hiddenOne clicked.");
            }
        });

        hiddenTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "hiddenTwo clicked.");
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Float value = (float)seekBar.getProgress();
                Float scaledValue = (value/MAX_SEEKBAR_VALUE)*MAX_LED_INTENSITY;
                Log.d(TAG, "seekBar value: "+value+"| scaled LED intensity: "+scaledValue);
                MQTTConnection.publishBlocking("D0="+scaledValue.intValue()+";", "devices/LED_0/value");
            }
        });

        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "toggleButton clicked, state is: "+isChecked);
            }
        });


    }

    public void hideElements() {
        hiddenOne.setVisibility(View.GONE);
        hiddenOne.setClickable(false);
        hiddenTwo.setVisibility(View.GONE);
        hiddenTwo.setClickable(false);
        seekBar.setVisibility(View.GONE);
        seekBar.setClickable(false);
        toggleButton.setVisibility(View.GONE);
        toggleButton.setClickable(false);
    }
}
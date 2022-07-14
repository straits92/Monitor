package com.example.monitor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;

public class DeviceActivity extends AppCompatActivity {

    /* declare display elements here */
    private AutoCompleteTextView dropDownListDevices;
    private Button navigateToSensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        navigateToSensors = findViewById(R.id.idNavigateToSensors);

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

        // create and destroy buttons within the device control panel based on which
        // device has been selected. how can this be done? for each element individually,
        // or can the entire constraintlayout with everything in it be destroyed/created
        // at will?

    }
}
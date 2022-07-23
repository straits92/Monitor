package com.example.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
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
import com.example.monitor.repositories.networkutils.TopicData;
import com.example.monitor.viewmodels.DeviceActivityViewModel;
import com.example.monitor.viewmodels.MainActivityViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.google.api.LogDescriptor;

import java.util.List;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = "DeviceActivity";
    private static Float MAX_LED_INTENSITY = 35.0f;
    private static Float MAX_SEEKBAR_VALUE = 100.0f;

    private DeviceActivityViewModel deviceViewModel;

    /* declare display elements here */
    private AutoCompleteTextView dropDownListDevices;
    private Button navigateToSensors;
    private Button hiddenOne;
    private SeekBar seekBar;
    private Switch toggleButton;
    private Button hiddenTwo;

    private Integer LEDIntensity;
    static final String LED = "LEDIntensity";
    private Bundle state;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState: saving LEDIntensity :"+LEDIntensity);
        deviceViewModel.setLEDIntensity(LEDIntensity);
        outState.putInt(LED, LEDIntensity);
        super.onSaveInstanceState(outState);
    }

    /* currently the savedInstanceState bundles return as null; not usable for saving */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            LEDIntensity = savedInstanceState.getInt(LED);
            Log.d(TAG, "onRestoreInstanceState: restoring LEDIntensity from state: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        } else {
            LEDIntensity = deviceViewModel.getLEDIntensity();
            Log.d(TAG, "onRestoreInstanceState: restoring LED intensity from VM variable: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state != null) {
            LEDIntensity = state.getInt(LED);
            Log.d(TAG, "onResume: getting LED intensity from state: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        } else {
            LEDIntensity = deviceViewModel.getLEDIntensity();
            Log.d(TAG, "onResume: getting LED intensity from VM variable: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) { //must the state be passed to the activity?
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        if (savedInstanceState != null) {
            LEDIntensity = savedInstanceState.getInt(LED);
            seekBar.setProgress(LEDIntensity);
            Log.d(TAG, "onCreate: restoring LEDIntensity: "+LEDIntensity);
            state = savedInstanceState;
        } else {
            Log.d(TAG, "onCreate: savedInstanceState created anew");
        }

        /* set up the viewmodel */
        deviceViewModel = new ViewModelProvider(this).get(DeviceActivityViewModel.class);

        MQTTConnection.publishBlocking(TAG+"_MonitorApp_test", "general");

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
                // if recreating it is needed
//                Intent intent = new Intent(DeviceActivity.this, MainActivity.class);
//                startActivity(intent);
                Intent openMainActivity = new Intent(DeviceActivity.this, MainActivity.class);
                openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivityIfNeeded(openMainActivity, 0);
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

        /* for the test buttons */
        hiddenOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "hiddenOne clicked.");
                MQTTConnection.getRetainedMsgFromTopic(TopicData.getJsonSensorData());

            }
        });

        hiddenTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "hiddenTwo clicked.");
            }
        });
        /* ... */


        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Integer seekBarIntValue = seekBar.getProgress();
                Float value = (float)seekBarIntValue;
                Float scaledValue = (value/MAX_SEEKBAR_VALUE)*MAX_LED_INTENSITY;
                Log.d(TAG, "seekBar value: "+value+"| scaled LED intensity: "+scaledValue);
                MQTTConnection.publishBlocking("D0="+scaledValue.intValue()+";", TopicData.getDeviceTopics(0));
                LEDIntensity = seekBar.getProgress();
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
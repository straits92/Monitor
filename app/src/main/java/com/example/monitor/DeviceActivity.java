package com.example.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
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
import android.widget.Toast;

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
    private Context appContext;

    /* declare display elements here */
    private AutoCompleteTextView dropDownListDevices;
    private Button navigateToSensors;
    private Button hiddenOne;
    private SeekBar seekBar;
    private Button hiddenTwo;
    private Switch ledLdrSwitch;

    private Integer LEDIntensity;
    static final String LED = "LEDIntensity";
    private Bundle state;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
//        Log.d(TAG, "onSaveInstanceState: saving LEDIntensity :"+LEDIntensity);
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
//            Log.d(TAG, "onResume: getting LED intensity from state: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        } else {
            LEDIntensity = deviceViewModel.getLEDIntensity();
//            Log.d(TAG, "onResume: getting LED intensity from VM variable: " + LEDIntensity);
            seekBar.setProgress(LEDIntensity);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        appContext = getApplication();

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
        navigateToSensors = findViewById(R.id.idNavigateToSensors);
        hiddenOne = findViewById(R.id.dummyButtonForHideShow1);
        hiddenTwo = findViewById(R.id.dummyButtonForHideShow2);
        seekBar = findViewById(R.id.varyingOutputSeekBar);
        ledLdrSwitch = findViewById(R.id.LED_LDR_switch);
        hideElements();

        /* set up dropdown list based on hardcoded parameters */
        String[] monitorDevices = getResources().getStringArray(R.array.monitoring_devices);
        ArrayAdapter dropDownListDevicesAdapter = new ArrayAdapter(this,
                R.layout.dropdown_item_monitoring_parameter, monitorDevices);
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
                    ledLdrSwitch.setVisibility(View.VISIBLE);
                    ledLdrSwitch.setClickable(true);
                } else if (selectedParameter.equals("Placeholder 1")) {
                    hiddenOne.setVisibility(View.VISIBLE);
                    hiddenOne.setClickable(true);
                } else if (selectedParameter.equals("Placeholder 2")) {
                    hiddenTwo.setVisibility(View.VISIBLE);
                    hiddenTwo.setClickable(true);
                } else {}

            }
        });

        ledLdrSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (MQTTConnection.publishAsync("D0=0;",
                            TopicData.getDeviceTopics(0)) != MonitorEnums.MQTT_CONNECTED) {
                        Log.d(TAG, "ledLdrSwitch, onCheckedChanged: NO MQTT CONNECTION");
                        Toast.makeText(appContext,
                                "Client not connected to MQTT", Toast.LENGTH_SHORT).show();
                        MQTTConnection.connectAsync();
                    } else { // connected
                        MQTTConnection.publishAsync("M0=1;", TopicData.getDeviceModeTopics(0));

                    }
                    seekBar.setProgress(0);
                    LEDIntensity = seekBar.getProgress();
                    seekBar.setClickable(false);
                    seekBar.setEnabled(false);
                } else {
                   if (MQTTConnection.publishAsync("M0=0;",
                           TopicData.getDeviceTopics(0)) != MonitorEnums.MQTT_CONNECTED) {
                       Log.d(TAG, "ledLdrSwitch, onCheckedChanged: NO MQTT CONNECTION");
                       Toast.makeText(appContext,
                               "Client not connected to MQTT", Toast.LENGTH_SHORT).show();
                       MQTTConnection.connectAsync();
                    }
                    seekBar.setClickable(true);
                    seekBar.setEnabled(true);
                }
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
                Integer seekBarIntValue = seekBar.getProgress();
                Float value = (float)seekBarIntValue;
                Float scaledValue = (value/MAX_SEEKBAR_VALUE)*MAX_LED_INTENSITY;
                Log.d(TAG, "seekBar value: "+value+"| scaled LED intensity: "+scaledValue);
                if (MQTTConnection.publishAsync("D0="+scaledValue.intValue()
                        +";", TopicData.getDeviceTopics(0)) != MonitorEnums.MQTT_CONNECTED) {
                    Log.d(TAG, "seekBar: NO MQTT CONNECTION");
                    Toast.makeText(appContext,
                            "Client not connected to MQTT", Toast.LENGTH_SHORT).show();
                    MQTTConnection.connectAsync();
                }
                LEDIntensity = seekBar.getProgress();
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
        ledLdrSwitch.setVisibility(View.GONE);
        ledLdrSwitch.setClickable(false);
    }
}
package com.example.monitor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.repositories.networkutils.TopicData;
import com.example.monitor.repositories.parseutils.ParseUtils;
import com.example.monitor.viewmodels.DeviceActivityViewModel;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;

import java.nio.charset.StandardCharsets;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = "DeviceActivity";
    private DeviceActivityViewModel deviceViewModel;
    private Context appContext;

    /* declare display elements here */
    private AutoCompleteTextView dropDownListDevices;
    private Button navigateToSensors;
    private Button hiddenOne;
    private SeekBar seekBar;
    private Button hiddenTwo;
    private Switch ledLdrSwitch;
    private TextView deviceStatus;

    private Integer LEDIntensity;
    static final String LED = "LEDIntensity"; // key for fetching value from savedinstance
    private Bundle state;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
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
        } else {
            LEDIntensity = deviceViewModel.getLEDIntensity();
            Log.d(TAG, "onRestoreInstanceState: restoring LED intensity from VM variable: " + LEDIntensity);
        }
        seekBar.setProgress(LEDIntensity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state != null) {
            LEDIntensity = state.getInt(LED);
            seekBar.setProgress(LEDIntensity);
        } else {
            LEDIntensity = deviceViewModel.getLEDIntensity();
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
        deviceStatus = findViewById(R.id.deviceStatus);
        hideDeviceControlElements();

        /* set up dropdown list based on hardcoded parameters */
        String[] monitorDevices = getResources().getStringArray(R.array.monitoring_devices);
        ArrayAdapter dropDownListDevicesAdapter = new ArrayAdapter(this,
                R.layout.dropdown_item_monitoring_parameter, monitorDevices);
        dropDownListDevices = findViewById(R.id.dropDownDevicesText);
        dropDownListDevices.setAdapter(dropDownListDevicesAdapter);

        /*** set up the element listeners ***/
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
                hideDeviceControlElements();
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
                    } else {
                   }
                    seekBar.setClickable(true);
                    seekBar.setEnabled(true);
                }

                /* check if devices online by subscribing async to device status topic */
                // should run async, but if the WiFi module publishes that which pico
                // echoed too slowly, it will say "offline". so it's best to make this a
                // user-prompted functionality? either way it only makes sense when the
                // user is directly varying the output. if it's sensor-dependent, then
                // the app has no reference value to compare with the Pico echo.
                // so again in this case the criterium for the device being online
                // is the timestamp available in a json on its status topic.
                // checkIfDeviceOnline(MonitorEnums.LED_DEVICE, 0);
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

                /* check if devices online by subscribing async to device status topic */
                // deviceStatus.setText("OFFLINE");

                Integer seekBarIntValue = seekBar.getProgress();
                Float value = (float)seekBarIntValue;
                Float scaledValue = (value/MonitorConstants.MAX_SEEKBAR_VALUE)*MonitorConstants.MAX_LED_INTENSITY;
                Log.d(TAG, "seekBar value: "+value+"| scaled LED intensity: "+scaledValue);
                if (MQTTConnection.publishAsync("D0="+scaledValue.intValue()
                        +";", TopicData.getDeviceTopics(MonitorEnums.LED_DEVICE)) != MonitorEnums.MQTT_CONNECTED) {
                    Log.d(TAG, "seekBar: NO MQTT CONNECTION");
                    Toast.makeText(appContext,
                            "Client not connected to MQTT", Toast.LENGTH_SHORT).show();
                    MQTTConnection.connectAsync();
                }
                LEDIntensity = seekBar.getProgress();
            }
        });
    }

    /* criterium for device being online is if it echoes the value which was last sent to it */
    private void checkIfDeviceOnline(int deviceIndex, int deviceValue) {

        Mqtt5Client mqtt5Client = MQTTConnection.getClient();
        String topic = TopicData.getDeviceStatusTopics(deviceIndex);
        mqtt5Client.toAsync().subscribeWith().topicFilter(topic)/*.qos(MqttQos.AT_LEAST_ONCE)*/
                .callback(publish -> {
                    String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    System.out.println("Received data in callback, topic: " + topic + ", payload: " + payload);
                    mqtt5Client.toBlocking().unsubscribeWith().topicFilter(topic).send();
                    int deviceValueFromJson = 0; // ParseUtils.parseDeviceJsonTimestamp(payload);

                    // then setText
                    if (deviceValueFromJson == deviceValue) {
                        deviceStatus.setText("ONLINE");
                    } else {
                        deviceStatus.setText("OFFLINE");
                    }

                }).send();
    }

    public void hideDeviceControlElements() {
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
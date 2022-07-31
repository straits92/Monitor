package com.example.monitor.viewmodels;

import android.util.Log;
import androidx.lifecycle.ViewModel;

import com.example.monitor.repositories.networkutils.MQTTConnection;
import com.example.monitor.repositories.networkutils.TopicData;

public class DeviceActivityViewModel extends ViewModel {
//    private SavedStateHandle savedStateHandle;
    private static final String TAG = "DeviceActivityViewModel";
    private int LEDIntensity;

    public int getLEDIntensity() {return LEDIntensity;}
    public void setLEDIntensity(int LEDIntensity) {
        this.LEDIntensity = LEDIntensity;
    }

    public DeviceActivityViewModel(/*@NonNull Application application, SavedStateHandle handle*/) {
//        super(application);
        Log.d(TAG, "DeviceActivityViewModel: viewmodel created");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared: viewmodel destroyed; LED device turned off");
        MQTTConnection.publishBlocking("M0=0;", TopicData.getDeviceModeTopics(0));
        MQTTConnection.publishBlocking("D0=0;", TopicData.getDeviceTopics(0));
    }

}

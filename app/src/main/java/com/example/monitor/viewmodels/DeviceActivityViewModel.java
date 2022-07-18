package com.example.monitor.viewmodels;

import android.util.Log;
import androidx.lifecycle.ViewModel;

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
        Log.d(TAG, "DeviceActivityViewModel: vm created");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared: vm got destroyed");
    }

}

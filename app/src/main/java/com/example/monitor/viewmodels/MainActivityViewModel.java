package com.example.monitor.viewmodels;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.loader.content.AsyncTaskLoader;

import com.example.monitor.models.Temperature;
import com.example.monitor.repositories.DataRepository;

import java.util.ArrayList;
import java.util.List;

/* viewmodel for Temperature class */
public class MainActivityViewModel extends ViewModel {

    /* observable data */
    private MutableLiveData<ArrayList<Temperature>> mutableTempDataEntries;
    private final MutableLiveData<Boolean> isUpdating = new MutableLiveData<>();

    /* declare the repository module with which the viewmodel communicates */
    private DataRepository dataRepository;

    public void init () {

        if (mutableTempDataEntries != null) {
            return;
        }

        /* gets new repository singleton (causes instantiation of it), then the data set */
        dataRepository = DataRepository.getInstance();
        mutableTempDataEntries = dataRepository.getDataSet();

    }

    public void addNewValue(final Temperature temperatureEntry) {
        isUpdating.setValue(true);

        /* dummy AsyncTask simulating the addition of a new entry by sleeping the thread */
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPostExecute(Void unused) {
                super.onPostExecute(unused);
                ArrayList<Temperature> currentTempDataEntries = mutableTempDataEntries.getValue();
                currentTempDataEntries.add(temperatureEntry);
                mutableTempDataEntries.postValue(currentTempDataEntries);
                isUpdating.setValue(false);
            }

            @SuppressLint("StaticFieldLeak")
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();

    }

    public LiveData<Boolean> getIsUpdating() {
        return isUpdating;
    }

    public LiveData<ArrayList<Temperature>> getTempDataEntries() {
        return mutableTempDataEntries;
    }

}

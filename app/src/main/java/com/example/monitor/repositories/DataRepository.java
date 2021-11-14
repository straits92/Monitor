package com.example.monitor.repositories;

import androidx.lifecycle.MutableLiveData;

import com.example.monitor.models.Temperature;

import java.util.ArrayList;

/* NOT USED ANYMORE */
public class DataRepository {

    private static DataRepository instance;
    private ArrayList<Temperature> dataSet = new ArrayList<>(); // this should be array; but the containers in activity and viewmodel may need to be general lists

    public static DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    public MutableLiveData<ArrayList<Temperature>> getDataSet() {

        mimicDataRetrievalFromRemote();

        /* create mutable object, bind dataSet to it */
        MutableLiveData<ArrayList<Temperature>> data = new MutableLiveData<>();
        data.setValue(dataSet);

        return data;

    }

    private void mimicDataRetrievalFromRemote() {

        dataSet.add(new Temperature("10", "http://...", "0"));
        dataSet.add(new Temperature("11", "http://...", "1"));
        dataSet.add(new Temperature("12", "http://...", "2"));
        dataSet.add(new Temperature("13", "http://...", "3"));
        dataSet.add(new Temperature("14", "http://...", "4"));

    }

}

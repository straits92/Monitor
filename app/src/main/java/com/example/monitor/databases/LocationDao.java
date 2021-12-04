package com.example.monitor.databases;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.monitor.models.MonitorLocation;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    void insert(MonitorLocation monitorLocation);

    @Update
    void update(MonitorLocation monitorLocation);

    @Delete
    void delete(MonitorLocation monitorLocation);

    @Query("SELECT * FROM location_table ORDER BY id DESC") /* descending order */
    LiveData<List<MonitorLocation>> getLocationTable();

    @Query("DELETE FROM location_table")
    void deleteLocationTable();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocationList(List<MonitorLocation> monitorLocationList);
}

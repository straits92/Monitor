package com.example.monitor.databases;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.monitor.models.Location;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    void insert(Location location);

    @Update
    void update(Location location);

    @Delete
    void delete(Location location);

    @Query("SELECT * FROM location_table ORDER BY id DESC") /* descending order */
    LiveData<List<Location>> getLocationTable();

    @Query("DELETE FROM location_table")
    void deleteLocationTable();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocationList(List<Location> locationList);
}

package com.example.monitor.databases;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.monitor.models.Weather;

import java.util.List;

/* interface provides methods which are annotated; @Dao library implements this
* interface; it generates method bodies.
* Often one DAO is made per entity, this is for Weather */
@Dao
public interface WeatherDao {

    @Insert
    void insert(Weather weatherDataPoint);

    @Update
    void update(Weather weatherDataPoint);

    @Delete
    void delete(Weather weatherDataPoint);

    @Query("DELETE FROM weather_table")
    void deleteAllWeatherPoints();

    @Query("SELECT * FROM weather_table ORDER BY id DESC") /* descending order */
    LiveData<List<Weather>> getAllWeatherPoints();

//    @Query("DELETE FROM hourly_weather_table")
//    void deleteAllHourlyWeatherPoints();
//
//    @Query("SELECT * FROM hourly_weather_table ORDER BY id DESC") /* descending order */
//    LiveData<List<Weather>> getAllHourlyWeatherPoints();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWeatherList(List<Weather> weatherList);

}

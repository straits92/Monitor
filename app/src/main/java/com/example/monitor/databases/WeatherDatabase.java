package com.example.monitor.databases;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.monitor.models.Weather;
import com.example.monitor.backgroundutil.ExecutorHelper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Database(entities = {Weather.class}, version = 1)
public abstract class WeatherDatabase extends RoomDatabase {
    private static final String TAG = "WeatherDatabase:";
    private static WeatherDatabase instance;
    private WeatherDao weatherDao = weatherDao();

    /* code for method is autogenerated by Room */
    public abstract WeatherDao weatherDao();

    /* singleton */
    public static synchronized WeatherDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    WeatherDatabase.class,"weather_database")
                    .fallbackToDestructiveMigration()/* deletes previous version db content */
                    .addCallback(roomCallback)/* call right after creating the instance for setup  */
                    .build();
        }
        return instance;
    }

    /* could be used to set up dummy database data on a bg thread */
    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
        }
    };

}

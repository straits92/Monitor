package com.example.monitor.databases;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.monitor.backgroundutil.ExecutorHelper;
import com.example.monitor.models.MonitorLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/* handled by weather repository, not its own repository */
@Database(entities = {MonitorLocation.class}, version = 1)
public abstract class LocationDatabase extends RoomDatabase {
    private static final String TAG = "LocationDatabase:";
    private static LocationDatabase instance;
    private LocationDao locationDao = locationDao();

    /* code for method is autogenerated by Room */
    public abstract LocationDao locationDao();

    /* turn this class into a singleton; always use same instance everywhere */
    public static synchronized LocationDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    LocationDatabase.class,"location_database")
                    .fallbackToDestructiveMigration()/* deletes previous version db content */
                    .addCallback(roomCallback)/* call right after creating the instance for setup,but in which thread  */
                    .build();
        }
        return instance;
    }

    /* could be used to set up database initial location */
    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
        }
    };

}

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

    private static ExecutorService executor;
    private LocationDao locationDao = locationDao();

    /* code for method is autogenerated by Room */
    public abstract LocationDao locationDao();

    /* turn this class into a singleton; always use same instance everywhere */
    public static synchronized LocationDatabase getInstance(Context context) {
        if (instance == null) {
            executor = ExecutorHelper.getSingleThreadExecutorInstance();
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    LocationDatabase.class,"location_database")
                    .fallbackToDestructiveMigration()/* deletes previous version db content */
                    .addCallback(roomCallback)/* call right after creating the instance for setup,but in which thread  */
                    .build();

            Log.d(TAG, "getInstance: LocationDatabase instantiated!");
        }
        return instance;
    }

    /* sets up its own initial location */
    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

//            new PopulateLocationDbAsyncTask(instance).execute();

            /* with the callable is currently not working */
        }
    };

    private static class PopulateLocationDbAsyncTask extends AsyncTask<Void, Void, Void> {
        private LocationDao dao;

        private PopulateLocationDbAsyncTask(LocationDatabase db) {
            dao = db.locationDao();
        }

        @Override
        protected Void doInBackground(Void... voids) {

            List<MonitorLocation> defaultMonitorLocationList = new ArrayList<>();
            MonitorLocation defaultHomeLocation = new MonitorLocation("298198", "Belgrade",
                    "44.8125", "20.4612", false, 0);
            MonitorLocation defaultVariableLocation = new MonitorLocation("298486", "Novi Sad",
                    "45.267136", "19.833549", false, 1);
            defaultMonitorLocationList.add(0, defaultHomeLocation);
            defaultMonitorLocationList.add(1, defaultVariableLocation);

            Log.d(TAG, "locationDb: initial caching of default locations attempted here...");
            dao.deleteLocationTable();
            dao.insertLocationList(defaultMonitorLocationList);

            List<MonitorLocation> locationList = dao.getLocationTable().getValue();

            if (locationList == null) {
                Log.d(TAG, "locationDb: fetched locationList is null...");
            }
            return null;
        }
    }

}

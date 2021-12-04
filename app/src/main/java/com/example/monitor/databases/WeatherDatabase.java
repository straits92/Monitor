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

    /* turn this class into a singleton; always use same instance everywhere */
    private static WeatherDatabase instance;

    private static ExecutorService executor;
    private WeatherDao weatherDao = weatherDao();

    /* code for method is autogenerated by Room */
    public abstract WeatherDao weatherDao();

    /* singleton */
    public static synchronized WeatherDatabase getInstance(Context context) {
        if (instance == null) {
            executor = ExecutorHelper.getSingleThreadExecutorInstance();
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    WeatherDatabase.class,"weather_database")
                    .fallbackToDestructiveMigration()/* deletes previous version db content */
                    .addCallback(roomCallback)/* call right after creating the instance for setup  */
                    .build();

            Log.d(TAG, "getInstance: WeatherDatabase instantiated!");
        }
        return instance;
    }

    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            /* dummy populate database here, on a bg thread */

            /* with the asynctask*/
//            new PopulateDbBackgroundTask(instance).execute();

            /* with the callable; currently not working */
//            try {
//                Log.d(TAG, "Loading initial database...");
//                instance.initialDatabaseContent();
//                Log.d(TAG, "Loaded initial database!");
//            } catch (ExecutionException | InterruptedException e) {
//                Log.d(TAG, "roomCallback: initialDatabaseContent: Exception thrown!");
//                e.printStackTrace();
//            }

        }
    };

    private static class PopulateDbBackgroundTask extends AsyncTask<Void, Void, Void> {
        private WeatherDao weatherDao;

        private PopulateDbBackgroundTask(WeatherDatabase db) {
            weatherDao = db.weatherDao();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "PopulateDbBackgroundTask bg thread: called !!!!!!!!!!!");
//            weatherDao.insert(new Weather("1", "ASyncTask_weatherdb", "0"));
//            weatherDao.insert(new Weather("1", "ASyncTask_weatherdb", "1"));
            Log.d(TAG, "PopulateDbBackgroundTask bg thread: dummy weather points inserted!!!!!!!!!!");
            return null;
        }
    }

    /* populate database background task via callable; currently doesn't work.
    * because this is an abstract class?  */
    public synchronized Void initialDatabaseContent()
            throws ExecutionException, InterruptedException {
        Future<Void> task = executor.submit(new Callable<Void>() {
            @Override
            public Void call() {
                /* !!! weatherDao.insert() never executes here !!! */
                Log.d(TAG, "from Callable bg thread: dummy weather points to be inserted...");
//                weatherDao.insert(new Weather("1", "CALLABLE_weatherdb", "0"));
//                weatherDao.insert(new Weather("2", "CALLABLE_weatherdb", "0"));
                Log.d(TAG, "from Callable bg thread: dummy weather points inserted!!!!!!!!!!!!!!!!!!");
                return null;
            }
        });
        return task.get();
    }

}

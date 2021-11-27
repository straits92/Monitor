package com.example.monitor.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_table")
public class MonitorLocation {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String location; // the location code
    private String localizedName; // city name
    private String latitude;
    private String longitude;
    private boolean isGpsAvailable;

    public MonitorLocation(String location, String localizedName, String latitude, String longitude, boolean isGpsAvailable) {
        this.location = location;
        this.localizedName = localizedName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isGpsAvailable = isGpsAvailable;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public void setGpsAvailable(boolean gpsAvailable) {
        isGpsAvailable = gpsAvailable;
    }

    public int getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public boolean isGpsAvailable() {
        return isGpsAvailable;
    }
}

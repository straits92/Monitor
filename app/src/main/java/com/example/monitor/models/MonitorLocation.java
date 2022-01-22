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
    private Integer locationType; /* 0: home city; 1: variable, 2: ... */

    public MonitorLocation(String location, String localizedName, String latitude, String longitude,
                           boolean isGpsAvailable, Integer locationType) {
        this.location = location;
        this.localizedName = localizedName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isGpsAvailable = isGpsAvailable;
        this.locationType = locationType;
    }

    public MonitorLocation(MonitorLocation monitorLocation) {
        this.location = monitorLocation.getLocation();
        this.localizedName = monitorLocation.getLocalizedName();
        this.latitude = monitorLocation.getLatitude();
        this.longitude = monitorLocation.getLongitude();
        this.isGpsAvailable = monitorLocation.isGpsAvailable();
        this.locationType = monitorLocation.getLocationType();

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

    public void setLocationType(Integer locationType) {
        this.locationType = locationType;
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

    public Integer getLocationType() {
        return locationType;
    }
}

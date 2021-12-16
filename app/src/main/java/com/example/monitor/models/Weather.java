package com.example.monitor.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/* Room creates an SQLite table for Weather */
@Entity(tableName = "weather_table")
public class Weather {

    @PrimaryKey(autoGenerate = true)
    private int id;

    public void setCelsius(String celsius) {
        this.celsius = celsius;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPersistence(Integer persistence) {
        this.persistence = persistence;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public void setTimeInMillis(long timeInMillis) {
        this.timeInMillis = timeInMillis;
    }

    private String celsius;
    private String link;
    private String time;
    private String location; /* which location the data is relevant to */
    private Integer persistence; /* 0: younger than 24h; 1: younger than a week; 2: up for deletion, older than a week */
    private Integer category; /* 1: hourly, 0: 12h, 2: raspberry sensor */
    private long timeInMillis;
    /* add humidity etc ... */


    public Weather(String celsius, String link, String time, String location, Integer persistence, Integer category, long timeInMillis) {
        this.celsius = celsius;
        this.link = link;
        this.time = time;
        this.location = location;
        this.persistence = persistence;
        this.category = category;
        this.timeInMillis = timeInMillis;
    }

    public int getId() {
        return id;
    }

    public String getCelsius() {
        return celsius;
    }

    public String getLink() {
        return link;
    }

    public String getTime() {
        return time;
    }

    public String getLocation() {
        return location;
    }

    public Integer getPersistence() {
        return persistence;
    }

    public Integer getCategory() {
        return category;
    }

    public long getTimeInMillis() {
        return timeInMillis;
    }


}

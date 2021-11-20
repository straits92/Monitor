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

    private String celsius;
    private String link;
    private String time;
    /* humidity etc */

    public Weather(String celsius, String link, String time) {
        this.celsius = celsius;
        this.link = link;
        this.time = time;
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
}

/* Weather member "category" states if data point is a 12-hour data point, an hourly data point,
 * or an hourly data point fetched from raspberry sensor; these classifications of data points
 * are aggregated in the same database chronologically, but routed into separate displays/trends.
 * one coherent display is done for one location; currently, just the "current" location */
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

    public void setHumidity(String humidity) {
        this.humidity = humidity;
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
    private String humidity;
    private String link;
    private String time;
    private String location; /* this is the string name of the city in which temperature measured  */
    private Integer persistence;
    private Integer category;
    private long timeInMillis;
    /* add other parameters... */


    public Weather(String celsius, String humidity, String link, String time, String location,
                   Integer persistence, Integer category, long timeInMillis) {
        this.celsius = celsius;
        this.humidity = humidity;
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

    public String getHumidity() {
        return humidity;
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

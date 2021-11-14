package com.example.monitor.models;

/* NOT USED ANYMORE */
public class Temperature {

    private String celsius;
    private String link;
    private String time;

    public Temperature(String celsius, String link, String time) {
        this.celsius = celsius;
        this.link = link;
        this.time = time;
    }

    public String getCelsius() {
        return celsius;
    }

    public void setCelsius(String celsius) {
        this.celsius = celsius;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}

package com.example.monitor;

public class MonitorEnums {
    public static boolean USE_MQTT = true;
    public static boolean USE_NGROK = false;

    /* enumerated constants for data sources (sensor, API) */
    public static final Integer HOME_SENSOR_INSTANT = 3;
    public static final Integer HOME_SENSOR = 2;
    public static final Integer SINGLE_HOUR_DATA = 1;
    public static final Integer TWELVE_HOURS_DATA = 0;

    /* enums for duration */
    public static final Integer UNDER_48H = 0;
    public static final Integer BETWEEN_48H_AND_WEEK = 1;
    public static final Integer MORE_THAN_A_WEEK = 2;

    /* constants for parameter selection */
    public static final Integer TEMPERATURE = 0;
    public static final Integer HUMIDITY = 1;
    public static final Integer BRIGHTNESS = 2;

    /* enumerated constants for temperatur values */
    public static final Integer MAXTEMP = 45;
    public static final Integer MINTEMP = -15;
}

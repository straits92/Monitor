package com.example.monitor;

public class MonitorConstants {

    /* constants for temperature values */
    public static final Integer MAXTEMP = 45;
    public static final Integer MINTEMP = -15;

    /* constants for LED device value*/
    public static final Float MAX_LED_INTENSITY = 35.0f;
    public static final Float MAX_SEEKBAR_VALUE = 100.0f;

    /* scheduling parameters in seconds */
    public static final Integer VISIBILITY_DURATION = 170000; /* 172800 is 60 * 60 * 24 * 2 , 48H */
    public static final Integer STORAGE_DURATION = 600000; /* 604800 is 60 * 60 * 24 * 7, should be a week */

    public static final Integer INITIAL_DELAY_HOURLY = 10;
    public static final Integer INITIAL_DELAY_TWELVE_HOURS = 20;
    public static final Integer INITIAL_DELAY_MAINTENANCE = 5;
    public static final Integer PERIODIC_DELAY_HOURLY = 1200;
    public static final Integer PERIODIC_DELAY_TWELVE_HOURS = 3600*12; // every 12 hrs 3600*12
    public static final Integer PERIODIC_DELAY_MAINTENANCE = 3600; // make it once a day? 3600 for now

    /* duration in millis */
    public static final Integer TEN_SECONDS = 10000;
    public static final Integer TEN_MINUTES = 600000;
    public static final Integer ONE_HOUR = 3600000;
    public static final Integer TWO_HOURS = 3600000*2;
    public static final Integer ONE_DAY = 86400000;
    public static final Integer STD_TIMEOUT = 4000;
    public static final Integer TWO_MINUTES = 120000;
    public static final Integer TIMEZONE_OFFSET = MonitorConstants.TWO_HOURS;


    public static final String SENSOR_READING_FORMAT = "VX;TX|";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";

}

package com.example.monitor.repositories.networkutils;

public class TopicData {
    private static String[] deviceTopics= {"devices/LED_0/value"};
    private static String generalTopic = "general";
    private static String jsonSensorData = "sensors/json";
    private static String jsonSensorHourlyDataTopic = "sensors/json/hourly";
    private static String jsonSensorInstantDataTopic = "sensors/json/instant";

    public static String getDeviceTopics(int index) {
        return deviceTopics[index];
    }

    public static String getGeneralTopic() {
        return generalTopic;
    }

    public static String getJsonSensorData() {
        return jsonSensorData;
    }

    public static String getJsonSensorHourlyDataTopic() {
        return jsonSensorHourlyDataTopic;
    }

    public static String getJsonSensorInstantDataTopic() {
        return jsonSensorInstantDataTopic;
    }

}

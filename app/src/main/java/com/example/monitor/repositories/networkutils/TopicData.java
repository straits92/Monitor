package com.example.monitor.repositories.networkutils;

public class TopicData {
    private static String[] deviceTopics= {"devices/LED_0/value"};
    private static String generalTopic = "general";
    private static String jsonSensorData = "sensors/json";

    public static String getDeviceTopics(int index) {
        return deviceTopics[index];
    }

    public static String getGeneralTopic() {
        return generalTopic;
    }

    public static String getJsonSensorData() {
        return jsonSensorData;
    }
}

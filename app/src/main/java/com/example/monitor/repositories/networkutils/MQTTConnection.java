package com.example.monitor.repositories.networkutils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import com.example.monitor.models.Weather;
import com.example.monitor.repositories.execmodel.CacheDataInDbsTask;
import com.example.monitor.repositories.parseutils.ParseUtils;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MQTTConnection {
    private static final String TAG = "MQTTConnection";
    private static MQTTConnection instance;
    private static Mqtt5Client client;

    public static Mqtt5Client getClient() {
        return client;
    }

    public static MQTTConnection getInstance() {
        if (instance == null) {
            String MQTTHOST = BrokerData.getMQTTHOST();
            Integer MQTTPORT = BrokerData.getMQTTPORT();
            String MQTTUSER = BrokerData.getMQTTUSER();
            String MQTTPW = BrokerData.getMQTTPW();
            instance = new MQTTConnection();
            client = Mqtt5Client.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost(MQTTHOST)
                    .serverPort(MQTTPORT)
                    .sslWithDefaultConfig()
                    .simpleAuth()
                    .username(MQTTUSER)
                    .password(MQTTPW.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .build();

        }

        return instance;
    }

    /*** methods to be used across activities and layers ***/
    public static String connectBlocking() {
        try {
            Mqtt5ConnAck connAckMessage = client.toBlocking().connect();
            //success
            Log.d(TAG, connAckMessage.getReasonCode().toString());
            return connAckMessage.getReasonCode().toString();
        } catch (Exception e) {
            //failure
            Log.d(TAG, e.getMessage());
            return e.getMessage();
        }
    }

    public static int publishBlocking(String payload, String topic) {

        // check connectivity; if dropped, reconnect. if fails, skip and exit with error
        Log.d(TAG, "publishBlocking, state: "+client.toBlocking().getState());

        client.toBlocking().publishWith()
                .topic(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload.getBytes())
                .send();

        return 0;

    }

    public static int subscribeBlocking(String topic) {
        client.toAsync().subscribeWith().topicFilter(topic)/*.qos(MqttQos.AT_LEAST_ONCE)*/
                .callback(publish -> {System.out.println("Received message on topic " +
                        publish.getTopic() + ": " +
                        new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8));}).send();
        return 0;
    }

    /* subscribe asynchronously, get last retained message on topic, then unsubscribe */
    public static void getRetainedMsgFromTopic(String topic) {
        client.toAsync().subscribeWith().topicFilter(topic)/*.qos(MqttQos.AT_LEAST_ONCE)*/
                .callback(publish -> {
                    String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    System.out.println("Received message on topic " +
                        publish.getTopic() + ": " +
                        payload);
                    client.toBlocking().unsubscribeWith().topicFilter(topic).send();
                }).send();
    }

    // methods for dealing with connectivity issues?

}

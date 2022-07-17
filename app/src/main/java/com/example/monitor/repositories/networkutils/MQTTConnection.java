package com.example.monitor.repositories.networkutils;

import android.util.Log;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5SubscribeBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MQTTConnection {

    static final String TAG = "MQTTConnection";

    private static MQTTConnection instance;

    /* MQTT credentials; maybe instantiate a secret object instead */
    static String MQTTHOST = "feb3ad1f8f6e4c18bcec81324db120b6.s1.eu.hivemq.cloud";
    static Integer MQTTPORT = 8883;
    static String MQTTUSER = "straits92_0";
    static String MQTTPW = "M0nitorLED";

    static Mqtt5Client client;

    // singleton
    public static MQTTConnection getInstance() {
        if (instance == null) {
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

//        try (final Mqtt5BlockingClient.Mqtt5Publishes publishes = client.toBlocking().publishes(MqttGlobalPublishFilter.ALL)) {
//
//            client.toBlocking().subscribeWith().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).send();
//
//            String responseHeader = publishes.receive().toString();
//            String responseContent = publishes.receive().getPayloadAsBytes();
//            Log.d(TAG, "Received response header: "+responseHeader+"| and response content: "+responseContent);
//
//            publishes.receive(1, TimeUnit.SECONDS).ifPresent(System.out::println);
//
//
//        } catch (Exception e) {
//            Log.d(TAG, "Subscription exception: "+e.toString());
//        }


        return 0;
    }

    // request current data from a topic
    public static String requestDataFromTopic(String topic) {

        return "obtained";
    }


}

package com.example.monitor.repositories.networkutils;

import android.util.Log;

import com.hivemq.client.internal.mqtt.handler.subscribe.MqttSubscriptionHandler;
import com.hivemq.client.internal.mqtt.message.subscribe.suback.MqttSubAck;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.Mqtt5Message;
import com.hivemq.client.mqtt.mqtt5.message.Mqtt5MessageType;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5SubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MQTTConnection {
    private static final String TAG = "MQTTConnection";
    private static MQTTConnection instance;
    private static Mqtt5Client client;

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

    // needs methods to check connection and reconnect if necessary


    // needs option for async reconnection, async topic reading (upon subscribing) and async publishing




}

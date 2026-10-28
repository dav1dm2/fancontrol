package com.example.fancontrol.mqtt;

import com.example.fancontrol.service.DeviceService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttInboundHandler {

    private final DeviceService deviceService;

    public MqttInboundHandler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload().toString();

        System.out.println(">>> MQTT INBOUND topic=" + topic + " payload=" + payload);

        if (topic == null) return;

        String[] parts = topic.split("/");
        if (parts.length < 3 || !"fan".equals(parts[0])) {
            System.out.println(">>> ignoring (bad topic format)");
            return;
        }

        String deviceName = parts[1];
        String action = parts[2];

        switch (action) {
            case "heartbeat" -> {
                System.out.println(">>> heartbeat for " + deviceName);
                deviceService.onHeartbeat(deviceName);
            }
            case "motion" -> {
                System.out.println(">>> motion for " + deviceName);
                deviceService.onMotion(deviceName);
            }
            case "status" -> {
                System.out.println(">>> status for " + deviceName);
                deviceService.onStatus(deviceName, payload);
            }
            case "state" -> {
                System.out.println(">>> state for " + deviceName);
                deviceService.onStateReport(deviceName, payload);
            }
            default -> System.out.println(">>> unknown action: " + action);
        }
    }
}
package com.example.fancontrol.service;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    public DeviceService(DeviceRepository deviceRepository,
                         @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
                         ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
    }

    // ---------- Existing CRUD ----------

    public List<Device> getDevices() {
        return deviceRepository.findAll();
    }

    public Device getDeviceById(int id) {
        return deviceRepository.findById(id).orElse(null);
    }

    public Device addDevice(Device device) {
        if (device.getName() == null || device.getName().isBlank()) {
            throw new IllegalArgumentException("Device name cannot be empty.");
        }
        for (Device existing : deviceRepository.findAll()) {
            if (existing.getName().equalsIgnoreCase(device.getName())) {
                throw new IllegalArgumentException("A device with this name already exists.");
            }
        }
        device.setFanState(false);
        device.setStatus(Status.OFFLINE);
        device.setLastSeen(null);
        return deviceRepository.save(device);
    }

    public void removeDevice(int id) {
        Device device = deviceRepository.findById(id).orElse(null);
        if (device != null) {
            deviceRepository.delete(device);
        }
    }

    // ---------- Commands (server → device, via MQTT) ----------

    public Device turnFanOn(int id) {
        Device device = deviceRepository.findById(id).orElse(null);
        if (device == null) return null;
        if (device.getStatus() == Status.OFFLINE) {
            throw new IllegalStateException("Cannot control an offline device.");
        }
        publishCommand(device, true);
        device.setFanState(true);
        return deviceRepository.save(device);
    }

    public Device turnFanOff(int id) {
        Device device = deviceRepository.findById(id).orElse(null);
        if (device == null) return null;
        publishCommand(device, false);
        device.setFanState(false);
        return deviceRepository.save(device);
    }

    private void publishCommand(Device device, boolean fanOn) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("fan", fanOn));
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader(MqttHeaders.TOPIC,
                                    "fan/" + device.getName() + "/command")
                            .setHeader(MqttHeaders.QOS, 1)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish MQTT command", e);
        }
    }

    // ---------- Inbound MQTT events ----------

    private Device findByDeviceName(String name) {
        return deviceRepository.findAll().stream()
                .filter(d -> d.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public void onHeartbeat(String deviceName) {
        System.out.println(">>> onHeartbeat called for name='" + deviceName + "'");
        Device d = findByDeviceName(deviceName);
        if (d == null) {
            System.out.println(">>> NO DEVICE FOUND with name='" + deviceName + "'");
            return;
        }
        System.out.println(">>> found device id=" + d.getId() + " name='" + d.getName() + "'");
        d.setStatus(Status.ONLINE);
        d.setLastSeen(LocalDateTime.now());
        deviceRepository.save(d);
        System.out.println(">>> saved device as ONLINE");
    }

    public void onMotion(String deviceName) {
        Device d = findByDeviceName(deviceName);
        if (d == null) return;
        d.setLastSensorActivation(LocalDateTime.now());
        d.setLastSeen(LocalDateTime.now());
        d.setStatus(Status.ONLINE);
        deviceRepository.save(d);
    }

    public void onStatus(String deviceName, String payload) {
        Device d = findByDeviceName(deviceName);
        if (d == null) return;
        if (payload.contains("offline")) {
            d.setStatus(Status.OFFLINE);
            d.setFanState(false);
        } else {
            d.setStatus(Status.ONLINE);
            d.setLastSeen(LocalDateTime.now());
        }
        deviceRepository.save(d);
    }

    public void onStateReport(String deviceName, String payload) {
        Device d = findByDeviceName(deviceName);
        if (d == null) return;
        boolean fanOn = payload.contains("true");
        d.setFanState(fanOn);
        d.setLastSeen(LocalDateTime.now());
        deviceRepository.save(d);
    }
}
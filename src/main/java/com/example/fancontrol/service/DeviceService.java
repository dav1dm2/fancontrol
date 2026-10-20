package com.example.fancontrol.service;

import com.example.fancontrol.exception.ApiException;
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
        return findDeviceOrThrow(id);
    }

    public Device addDevice(String name) {
        String normalizedName = validateName(name);
        ensureNameIsAvailable(normalizedName);

        Device device = new Device();
        device.setName(normalizedName);
        device.setFanState(false);
        device.setStatus(Status.OFFLINE);
        device.setLastSeen(null);
        return deviceRepository.save(device);
    }

    public Device updateDevice(int id, String name) {
        String normalizedName = validateName(name);
        Device device = findDeviceOrThrow(id);

        if (deviceRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw ApiException.conflict("A device with this name already exists.");
        }

        device.setName(normalizedName);
        return deviceRepository.save(device);
    }

    public void removeDevice(int id) {
        deviceRepository.delete(findDeviceOrThrow(id));
    }

    // ---------- Commands (server → device, via MQTT) ----------

    public Device turnFanOn(int id) {
        Device device = findDeviceOrThrow(id);
        if (device.getStatus() == Status.OFFLINE) {
            throw ApiException.conflict("Cannot control an offline device.");
        }
        publishCommand(device, true);
        device.setFanState(true);
        return deviceRepository.save(device);
    }

    public Device turnFanOff(int id) {
        Device device = findDeviceOrThrow(id);
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
            throw ApiException.serviceUnavailable("Failed to send the fan command to the MQTT broker.");
        }
    }

    private Device findDeviceOrThrow(int id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Device with id " + id + " was not found."));
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Device name cannot be empty.");
        }
        return name.trim();
    }

    private void ensureNameIsAvailable(String name) {
        if (deviceRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A device with this name already exists.");
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

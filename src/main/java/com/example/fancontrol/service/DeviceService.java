package com.example.fancontrol.service;

import com.example.fancontrol.dto.DeviceScheduleDto;
import com.example.fancontrol.dto.ScheduleEntryDto;
import com.example.fancontrol.exception.ApiException;
import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.ScheduleMode;
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
        device.setScheduleMode(ScheduleMode.MANUAL_OFF);
        device.setAutoShutoffEnabled(false);
        device.setAutoShutoffSeconds(300);
        device.setWeeklySchedule("[]");
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
        device.setScheduleMode(ScheduleMode.MANUAL_ON);
        publishCommand(device, true);
        device.setFanState(true);
        return deviceRepository.save(device);
    }

    public Device turnFanOff(int id) {
        Device device = findDeviceOrThrow(id);
        device.setScheduleMode(ScheduleMode.MANUAL_OFF);
        publishCommand(device, false);
        device.setFanState(false);
        return deviceRepository.save(device);
    }

    public Device updateSchedule(int id, DeviceScheduleDto scheduleDto) {
        Device device = findDeviceOrThrow(id);
        if (scheduleDto == null) {
            throw ApiException.badRequest("Schedule payload cannot be null.");
        }
        if (scheduleDto.mode() == null) {
            throw ApiException.badRequest("Schedule mode is required.");
        }
        if (scheduleDto.autoShutoffSeconds() < 5) {
            throw ApiException.badRequest("Auto-shutoff time must be at least 5 seconds.");
        }

        // Validate schedule entries
        if (scheduleDto.weeklySchedule() != null) {
            for (ScheduleEntryDto entry : scheduleDto.weeklySchedule()) {
                if (entry.dayOfWeek() < 1 || entry.dayOfWeek() > 7) {
                    throw ApiException.badRequest("Day of week must be between 1 (Monday) and 7 (Sunday).");
                }
                if (!isValidTime(entry.startTime()) || !isValidTime(entry.endTime())) {
                    throw ApiException.badRequest("Times must be in valid HH:mm format (e.g. 08:30).");
                }
            }
        }

        String scheduleJson;
        try {
            scheduleJson = objectMapper.writeValueAsString(
                    scheduleDto.weeklySchedule() != null ? scheduleDto.weeklySchedule() : List.of()
            );
        } catch (Exception e) {
            throw ApiException.badRequest("Failed to serialize weekly schedule.");
        }

        device.setScheduleMode(scheduleDto.mode());
        device.setAutoShutoffEnabled(scheduleDto.autoShutoffEnabled());
        device.setAutoShutoffSeconds(scheduleDto.autoShutoffSeconds());
        device.setWeeklySchedule(scheduleJson);

        Device saved = deviceRepository.save(device);
        publishScheduleConfig(saved, scheduleDto);
        return saved;
    }

    private boolean isValidTime(String time) {
        if (time == null || !time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return false;
        }
        return true;
    }

    private void publishScheduleConfig(Device device, DeviceScheduleDto scheduleDto) {
        try {
            String payload = objectMapper.writeValueAsString(scheduleDto);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader(MqttHeaders.TOPIC, "fan/" + device.getName() + "/config")
                            .setHeader(MqttHeaders.QOS, 1)
                            .setHeader(MqttHeaders.RETAINED, true)
                            .build()
            );
        } catch (Exception e) {
            throw ApiException.serviceUnavailable("Failed to send schedule configuration to MQTT broker.");
        }
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
        boolean wasOffline = (d.getStatus() == Status.OFFLINE);
        d.setStatus(Status.ONLINE);
        d.setLastSeen(LocalDateTime.now());
        Device saved = deviceRepository.save(d);
        System.out.println(">>> saved device as ONLINE");

        if (wasOffline) {
            syncScheduleToDevice(saved);
        }
    }

    public void syncScheduleToDevice(Device device) {
        List<ScheduleEntryDto> entries = List.of();
        if (device.getWeeklySchedule() != null && !device.getWeeklySchedule().isBlank()) {
            try {
                entries = objectMapper.readValue(device.getWeeklySchedule(), new com.fasterxml.jackson.core.type.TypeReference<List<ScheduleEntryDto>>() {});
            } catch (Exception ignored) {
            }
        }
        DeviceScheduleDto dto = new DeviceScheduleDto(
                device.getScheduleMode(),
                device.isAutoShutoffEnabled(),
                device.getAutoShutoffSeconds(),
                entries
        );
        publishScheduleConfig(device, dto);
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

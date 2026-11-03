package com.example.fancontrol.mapper;

import com.example.fancontrol.dto.DeviceResponse;
import com.example.fancontrol.dto.ScheduleEntryDto;
import com.example.fancontrol.model.Device;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DeviceMapper {

    private final ObjectMapper objectMapper;

    public DeviceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeviceResponse toResponse(Device device) {
        if (device == null) {
            return null;
        }

        List<ScheduleEntryDto> schedule = Collections.emptyList();
        if (device.getWeeklySchedule() != null && !device.getWeeklySchedule().isBlank()) {
            try {
                schedule = objectMapper.readValue(device.getWeeklySchedule(), new TypeReference<List<ScheduleEntryDto>>() {});
            } catch (Exception ignored) {
            }
        }

        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getStatus(),
                device.isFanState(),
                device.getLastSensorActivation(),
                device.getLastSeen(),
                device.getScheduleMode(),
                device.isAutoShutoffEnabled(),
                device.getAutoShutoffSeconds(),
                schedule
        );
    }
}

package com.example.fancontrol.dto;

import com.example.fancontrol.model.Status;

import java.time.LocalDateTime;

/** Public device representation returned by the API. */
public record DeviceResponse(
        int id,
        String name,
        Status status,
        boolean fanState,
        LocalDateTime lastSensorActivation,
        LocalDateTime lastSeen
) {
}

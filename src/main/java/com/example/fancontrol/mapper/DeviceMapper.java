package com.example.fancontrol.mapper;

import com.example.fancontrol.dto.DeviceResponse;
import com.example.fancontrol.model.Device;
import org.springframework.stereotype.Component;

@Component
public class DeviceMapper {

    public DeviceResponse toResponse(Device device) {
        if (device == null) {
            return null;
        }

        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getStatus(),
                device.isFanState(),
                device.getLastSensorActivation(),
                device.getLastSeen()
        );
    }
}

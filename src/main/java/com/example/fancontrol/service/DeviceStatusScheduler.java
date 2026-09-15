package com.example.fancontrol.service;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DeviceStatusScheduler {

    private final DeviceRepository deviceRepository;

    public DeviceStatusScheduler(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Scheduled(fixedRate = 10000)
    public void checkDevices() {

        LocalDateTime timeout =
                LocalDateTime.now().minusSeconds(30);

        List<Device> devices = deviceRepository.findAll();

        for (Device device : devices) {

            if (device.getLastSeen() == null) {
                continue;
            }

            if (device.getLastSeen().isBefore(timeout)) {

                device.setStatus(Status.OFFLINE);

                deviceRepository.save(device);
            }
        }
    }
}
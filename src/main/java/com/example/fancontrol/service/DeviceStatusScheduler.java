package com.example.fancontrol.service;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DeviceStatusScheduler {

    private final DeviceRepository deviceRepository;

    @Value("${mqtt.heartbeat.timeout.seconds}")
    private int timeoutSeconds;

    public DeviceStatusScheduler(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void checkDevices() {

        LocalDateTime timeout = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<Device> devices = deviceRepository.findAll();

        for (Device device : devices) {

            if (device.getLastSeen() == null) continue;
            if (device.getStatus() == Status.OFFLINE) continue;

            if (device.getLastSeen().isBefore(timeout)) {
                device.setStatus(Status.OFFLINE);
                device.setFanState(false);
                deviceRepository.save(device);
            }
        }
    }
}
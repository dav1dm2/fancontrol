package com.example.fancontrol.service;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

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

        for (Device existingDevice : deviceRepository.findAll()) {

            if (existingDevice.getName().equalsIgnoreCase(device.getName())) {
                throw new IllegalArgumentException(
                        "A device with this name already exists."
                );
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

    public Device turnFanOn(int id) {

        Device device = deviceRepository.findById(id).orElse(null);

        if (device == null) {
            return null;
        }

        if (device.getStatus() == Status.OFFLINE) {
            throw new IllegalStateException(
                    "Cannot control an offline device."
            );
        }

        device.setFanState(true);

        return deviceRepository.save(device);
    }

    public Device turnFanOff(int id) {

        Device device = deviceRepository.findById(id).orElse(null);

        if (device == null) {
            return null;
        }

        device.setFanState(false);

        return deviceRepository.save(device);
    }

    public Device heartbeat(int id) {

        Device device = deviceRepository.findById(id).orElse(null);

        if (device == null) {
            return null;
        }

        device.setStatus(Status.ONLINE);
        device.setLastSeen(LocalDateTime.now());

        return deviceRepository.save(device);
    }

}
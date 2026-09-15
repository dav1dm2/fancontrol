package com.example.fancontrol.controller;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<Device> getDevices() {
        return deviceService.getDevices();
    }

    @GetMapping("/{id}")
    public Device getDevice(@PathVariable int id) {
        return deviceService.getDeviceById(id);
    }

    @PostMapping
    public Device addDevice(@RequestBody Device device) {
        return deviceService.addDevice(device);
    }

    @DeleteMapping("/{id}")
    public void removeDevice(@PathVariable int id) {
        deviceService.removeDevice(id);
    }

    @PostMapping("/{id}/fan/on")
    public Device turnFanOn(@PathVariable int id) {

        return deviceService.turnFanOn(id);
    }

    @PostMapping("/{id}/fan/off")
    public Device turnFanOff(@PathVariable int id) {

        return deviceService.turnFanOff(id);
    }

    @PostMapping("/{id}/heartbeat")
    public Device heartbeat(@PathVariable int id) {
        return deviceService.heartbeat(id);
    }

}
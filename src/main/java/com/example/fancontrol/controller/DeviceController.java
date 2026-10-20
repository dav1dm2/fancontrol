package com.example.fancontrol.controller;

import com.example.fancontrol.dto.DeviceRequest;
import com.example.fancontrol.dto.DeviceResponse;
import com.example.fancontrol.mapper.DeviceMapper;
import com.example.fancontrol.model.Device;
import com.example.fancontrol.service.DeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;

    public DeviceController(DeviceService deviceService, DeviceMapper deviceMapper) {
        this.deviceService = deviceService;
        this.deviceMapper = deviceMapper;
    }

    @GetMapping
    public List<DeviceResponse> getDevices() {
        return deviceService.getDevices().stream().map(deviceMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DeviceResponse getDevice(@PathVariable int id) {
        return deviceMapper.toResponse(deviceService.getDeviceById(id));
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> addDevice(@RequestBody DeviceRequest request) {
        Device created = deviceService.addDevice(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public DeviceResponse updateDevice(@PathVariable int id, @RequestBody DeviceRequest request) {
        return deviceMapper.toResponse(deviceService.updateDevice(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeDevice(@PathVariable int id) {
        deviceService.removeDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fan/on")
    public DeviceResponse turnFanOn(@PathVariable int id) {
        return deviceMapper.toResponse(deviceService.turnFanOn(id));
    }

    @PostMapping("/{id}/fan/off")
    public DeviceResponse turnFanOff(@PathVariable int id) {
        return deviceMapper.toResponse(deviceService.turnFanOff(id));
    }
}

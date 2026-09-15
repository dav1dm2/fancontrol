package com.example.fancontrol.service;

import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    private Device sampleDevice;

    @BeforeEach
    void setUp() {
        sampleDevice = new Device();
        sampleDevice.setId(1);
        sampleDevice.setName("Living Room Fan");
        sampleDevice.setStatus(Status.ONLINE);
        sampleDevice.setFanState(false);
    }

    @Test
    void getDevices_returnsAllDevices() {
        when(deviceRepository.findAll()).thenReturn(List.of(sampleDevice));

        List<Device> result = deviceService.getDevices();

        assertEquals(1, result.size());
        assertEquals("Living Room Fan", result.getFirst().getName());
        verify(deviceRepository).findAll();
    }

    @Test
    void getDeviceById_deviceExists_returnsDevice() {
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));

        Device result = deviceService.getDeviceById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getDeviceById_deviceNotFound_returnsNull() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        Device result = deviceService.getDeviceById(99);

        assertNull(result);
    }

    @Test
    void addDevice_validDevice_savesAndReturnsDevice() {
        Device newDevice = new Device();
        newDevice.setName("Bedroom Fan");

        when(deviceRepository.findAll()).thenReturn(Collections.emptyList());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device d = invocation.getArgument(0);
            d.setId(2);
            return d;
        });

        Device created = deviceService.addDevice(newDevice);

        assertNotNull(created);
        assertEquals("Bedroom Fan", created.getName());
        assertEquals(Status.OFFLINE, created.getStatus());
        assertFalse(created.isFanState());
        verify(deviceRepository).save(newDevice);
    }

    @Test
    void addDevice_blankName_throwsIllegalArgumentException() {
        Device blankDevice = new Device();
        blankDevice.setName("   ");

        assertThrows(IllegalArgumentException.class, () -> deviceService.addDevice(blankDevice));
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void addDevice_duplicateName_throwsIllegalArgumentException() {
        Device duplicateDevice = new Device();
        duplicateDevice.setName("Living Room Fan");

        when(deviceRepository.findAll()).thenReturn(List.of(sampleDevice));

        assertThrows(IllegalArgumentException.class, () -> deviceService.addDevice(duplicateDevice));
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void removeDevice_deviceExists_deletesDevice() {
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));

        deviceService.removeDevice(1);

        verify(deviceRepository).delete(sampleDevice);
    }

    @Test
    void removeDevice_deviceNotFound_doesNothing() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        deviceService.removeDevice(99);

        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void turnFanOn_onlineDevice_turnsOnAndSaves() {
        sampleDevice.setStatus(Status.ONLINE);
        sampleDevice.setFanState(false);

        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(deviceRepository.save(sampleDevice)).thenReturn(sampleDevice);

        Device result = deviceService.turnFanOn(1);

        assertNotNull(result);
        assertTrue(result.isFanState());
        verify(deviceRepository).save(sampleDevice);
    }

    @Test
    void turnFanOn_offlineDevice_throwsIllegalStateException() {
        sampleDevice.setStatus(Status.OFFLINE);

        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));

        assertThrows(IllegalStateException.class, () -> deviceService.turnFanOn(1));
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void turnFanOn_deviceNotFound_returnsNull() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        Device result = deviceService.turnFanOn(99);

        assertNull(result);
    }

    @Test
    void turnFanOff_deviceExists_turnsOffAndSaves() {
        sampleDevice.setFanState(true);

        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(deviceRepository.save(sampleDevice)).thenReturn(sampleDevice);

        Device result = deviceService.turnFanOff(1);

        assertNotNull(result);
        assertFalse(result.isFanState());
        verify(deviceRepository).save(sampleDevice);
    }

    @Test
    void turnFanOff_deviceNotFound_returnsNull() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        Device result = deviceService.turnFanOff(99);

        assertNull(result);
    }
}

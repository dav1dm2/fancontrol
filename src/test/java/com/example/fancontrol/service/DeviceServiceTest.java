package com.example.fancontrol.service;

import com.example.fancontrol.exception.ApiException;
import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private MessageChannel mqttOutboundChannel;

    @Mock
    private ObjectMapper objectMapper;

    private DeviceService deviceService;
    private Device sampleDevice;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceRepository, mqttOutboundChannel, objectMapper);
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

        assertEquals(1, result.getId());
    }

    @Test
    void getDeviceById_deviceNotFound_throwsNotFound() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.getDeviceById(99));

        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void addDevice_trimsNameAndStartsOffline() {
        when(deviceRepository.existsByNameIgnoreCase("Bedroom Fan")).thenReturn(false);
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(2);
            return device;
        });

        Device created = deviceService.addDevice(" Bedroom Fan ");

        assertEquals(2, created.getId());
        assertEquals("Bedroom Fan", created.getName());
        assertEquals(Status.OFFLINE, created.getStatus());
        assertFalse(created.isFanState());
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void addDevice_blankName_throwsBadRequest() {
        ApiException exception = assertThrows(ApiException.class, () -> deviceService.addDevice("   "));

        assertEquals(400, exception.getStatus().value());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void addDevice_duplicateName_throwsConflict() {
        when(deviceRepository.existsByNameIgnoreCase("Living Room Fan")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.addDevice("Living Room Fan"));

        assertEquals(409, exception.getStatus().value());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void updateDevice_changesNameAndPreservesReportedState() {
        LocalDateTime activation = LocalDateTime.of(2026, 1, 1, 12, 0);
        sampleDevice.setFanState(true);
        sampleDevice.setLastSensorActivation(activation);
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(deviceRepository.existsByNameIgnoreCaseAndIdNot("Bedroom Fan", 1)).thenReturn(false);
        when(deviceRepository.save(sampleDevice)).thenReturn(sampleDevice);

        Device updated = deviceService.updateDevice(1, " Bedroom Fan ");

        assertEquals("Bedroom Fan", updated.getName());
        assertTrue(updated.isFanState());
        assertEquals(Status.ONLINE, updated.getStatus());
        assertEquals(activation, updated.getLastSensorActivation());
    }

    @Test
    void updateDevice_duplicateName_throwsConflict() {
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(deviceRepository.existsByNameIgnoreCaseAndIdNot("Bedroom Fan", 1)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.updateDevice(1, "Bedroom Fan"));

        assertEquals(409, exception.getStatus().value());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void updateDevice_blankName_throwsBadRequestBeforeLookup() {
        ApiException exception = assertThrows(ApiException.class, () -> deviceService.updateDevice(1, " "));

        assertEquals(400, exception.getStatus().value());
        verify(deviceRepository, never()).findById(anyInt());
    }

    @Test
    void removeDevice_existingDevice_deletesIt() {
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));

        deviceService.removeDevice(1);

        verify(deviceRepository).delete(sampleDevice);
    }

    @Test
    void removeDevice_missingDevice_throwsNotFound() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.removeDevice(99));

        assertEquals(404, exception.getStatus().value());
        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void turnFanOn_onlineDevice_publishesAndSaves() throws Exception {
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fan\":true}");
        when(deviceRepository.save(sampleDevice)).thenReturn(sampleDevice);

        Device result = deviceService.turnFanOn(1);

        assertTrue(result.isFanState());
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(messageCaptor.capture());
        assertEquals("{\"fan\":true}", messageCaptor.getValue().getPayload());
        assertEquals("fan/Living Room Fan/command", messageCaptor.getValue().getHeaders().get(MqttHeaders.TOPIC));
        assertEquals(1, messageCaptor.getValue().getHeaders().get(MqttHeaders.QOS));
        verify(deviceRepository).save(sampleDevice);
    }

    @Test
    void turnFanOn_offlineDevice_throwsConflictWithoutPublishing() {
        sampleDevice.setStatus(Status.OFFLINE);
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.turnFanOn(1));

        assertEquals(409, exception.getStatus().value());
        verify(mqttOutboundChannel, never()).send(any(Message.class));
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void turnFanOn_missingDevice_throwsNotFound() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.turnFanOn(99));

        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void turnFanOff_existingDevice_publishesAndSaves() throws Exception {
        sampleDevice.setFanState(true);
        when(deviceRepository.findById(1)).thenReturn(Optional.of(sampleDevice));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fan\":false}");
        when(deviceRepository.save(sampleDevice)).thenReturn(sampleDevice);

        Device result = deviceService.turnFanOff(1);

        assertFalse(result.isFanState());
        verify(mqttOutboundChannel).send(any(Message.class));
        verify(deviceRepository).save(sampleDevice);
    }

    @Test
    void turnFanOff_missingDevice_throwsNotFound() {
        when(deviceRepository.findById(99)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> deviceService.turnFanOff(99));

        assertEquals(404, exception.getStatus().value());
    }
}

package com.example.fancontrol.controller;

import com.example.fancontrol.exception.ApiException;
import com.example.fancontrol.exception.GlobalExceptionHandler;
import com.example.fancontrol.mapper.DeviceMapper;
import com.example.fancontrol.model.Device;
import com.example.fancontrol.model.Status;
import com.example.fancontrol.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceService deviceService;

    private MockMvc mockMvc;
    private Device sampleDevice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DeviceController(deviceService, new DeviceMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleDevice = new Device();
        sampleDevice.setId(1);
        sampleDevice.setName("Living Room Fan");
        sampleDevice.setStatus(Status.ONLINE);
        sampleDevice.setFanState(true);
    }

    @Test
    void getDevices_returnsTheExistingPageFields() throws Exception {
        when(deviceService.getDevices()).thenReturn(List.of(sampleDevice));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Living Room Fan"))
                .andExpect(jsonPath("$[0].status").value("ONLINE"))
                .andExpect(jsonPath("$[0].fanState").value(true))
                .andExpect(jsonPath("$[0].lastSensorActivation").value(nullValue()))
                .andExpect(jsonPath("$[0].lastSeen").value(nullValue()));
    }

    @Test
    void createDevice_returnsCreatedAndDeviceResponse() throws Exception {
        when(deviceService.addDevice("Bedroom Fan")).thenReturn(sampleDevice);

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bedroom Fan"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Living Room Fan"))
                .andExpect(jsonPath("$.fanState").value(true));
    }

    @Test
    void updateDevice_usesPutAndReturnsUpdatedRepresentation() throws Exception {
        Device updated = new Device();
        updated.setId(1);
        updated.setName("Bedroom Fan");
        updated.setStatus(Status.ONLINE);
        updated.setFanState(true);
        when(deviceService.updateDevice(1, "Bedroom Fan")).thenReturn(updated);

        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bedroom Fan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bedroom Fan"))
                .andExpect(jsonPath("$.fanState").value(true));
    }

    @Test
    void updateDevice_duplicateName_returnsConflictResponse() throws Exception {
        when(deviceService.updateDevice(1, "Living Room Fan"))
                .thenThrow(ApiException.conflict("A device with this name already exists."));

        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Living Room Fan\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("A device with this name already exists."))
                .andExpect(jsonPath("$.path").value("/api/devices/1"));
    }

    @Test
    void getDevice_missingDevice_returnsNotFoundResponse() throws Exception {
        when(deviceService.getDeviceById(99)).thenThrow(ApiException.notFound("Device with id 99 was not found."));

        mockMvc.perform(get("/api/devices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Device with id 99 was not found."));
    }

    @Test
    void updateDevice_blankName_returnsBadRequestResponse() throws Exception {
        when(deviceService.updateDevice(1, " ")).thenThrow(ApiException.badRequest("Device name cannot be empty."));

        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedJson_returnsBadRequestResponse() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{name:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request contains invalid or malformed data."));
    }

    @Test
    void deleteDevice_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/devices/1"))
                .andExpect(status().isNoContent());

        verify(deviceService).removeDevice(1);
    }
}

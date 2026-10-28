package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Input accepted when registering or updating a device. */
@Schema(description = "Dados para cadastro ou atualização de um dispositivo")
public record DeviceRequest(
        @Schema(description = "Nome único do dispositivo", example = "ESP32-LivingRoom")
        String name
) {
}

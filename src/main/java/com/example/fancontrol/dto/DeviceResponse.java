package com.example.fancontrol.dto;

import com.example.fancontrol.model.ScheduleMode;
import com.example.fancontrol.model.Status;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Public device representation returned by the API. */
@Schema(description = "Representação detalhada de um dispositivo")
public record DeviceResponse(
        @Schema(description = "Identificador único do dispositivo", example = "1")
        int id,

        @Schema(description = "Nome único do dispositivo", example = "ESP32-LivingRoom")
        String name,

        @Schema(description = "Status de conectividade (ONLINE ou OFFLINE)", example = "ONLINE")
        Status status,

        @Schema(description = "Estado atual do ventilador (true = ligado, false = desligado)", example = "false")
        boolean fanState,

        @Schema(description = "Data e hora da última detecção de movimento/sensor", example = "2026-10-28T18:30:00")
        LocalDateTime lastSensorActivation,

        @Schema(description = "Data e hora do último heartbeat ou sinal de presença recebido", example = "2026-10-28T19:00:00")
        LocalDateTime lastSeen,

        @Schema(description = "Modo de agendamento (MANUAL_ON, MANUAL_OFF, SCHEDULED)", example = "SCHEDULED")
        ScheduleMode scheduleMode,

        @Schema(description = "Se o auto-shutoff por inatividade está ativo", example = "true")
        boolean autoShutoffEnabled,

        @Schema(description = "Segundos para desligar após ausência de movimento", example = "300")
        int autoShutoffSeconds,

        @Schema(description = "Lista de horários semanais configurados")
        List<ScheduleEntryDto> weeklySchedule
) {
}

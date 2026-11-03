package com.example.fancontrol.dto;

import com.example.fancontrol.model.ScheduleMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Configuração de agendamento e automação do dispositivo")
public record DeviceScheduleDto(
        @Schema(description = "Modo de operação: MANUAL_ON, MANUAL_OFF ou SCHEDULED", example = "SCHEDULED")
        ScheduleMode mode,

        @Schema(description = "Se a função de desligamento automático por inatividade do sensor está ativada", example = "true")
        boolean autoShutoffEnabled,

        @Schema(description = "Tempo em segundos sem movimento para desligar automaticamente", example = "300")
        int autoShutoffSeconds,

        @Schema(description = "Lista de janelas de horários semanais ativos (dias 1 a 7)")
        List<ScheduleEntryDto> weeklySchedule
) {
}

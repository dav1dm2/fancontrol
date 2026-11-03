package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Item de horário semanal (dia da semana, início e fim em formato HH:mm)")
public record ScheduleEntryDto(
        @Schema(description = "Dia da semana (1 = Segunda-feira, 7 = Domingo)", example = "1")
        int dayOfWeek,

        @Schema(description = "Hora de início no formato HH:mm (24h)", example = "08:00")
        String startTime,

        @Schema(description = "Hora de término no formato HH:mm (24h)", example = "18:00")
        String endTime
) {
}

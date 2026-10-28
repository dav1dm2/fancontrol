package com.example.fancontrol.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estrutura padrão de resposta de erro da API")
public record ApiErrorResponse(
        @Schema(description = "Timestamp do momento do erro", example = "2026-10-28T22:15:00Z")
        Instant timestamp,

        @Schema(description = "Código HTTP do erro", example = "400")
        int status,

        @Schema(description = "Descrição resumida do status HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensagem amigável descrevendo o erro", example = "Device name cannot be blank.")
        String message,

        @Schema(description = "Caminho da URI requisitada", example = "/api/devices")
        String path
) {
}

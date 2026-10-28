package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo público do operador cadastrado")
public record UserSummaryResponse(
        @Schema(description = "Identificador do usuário", example = "2")
        Long id,

        @Schema(description = "Nome do usuário", example = "joao")
        String name
) {
}

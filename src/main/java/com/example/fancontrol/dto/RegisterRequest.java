package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados para cadastro de novo operador")
public record RegisterRequest(
        @Schema(description = "Nome do operador (único no sistema)", example = "joao")
        String name,

        @Schema(description = "Senha do operador (mínimo 6 caracteres)", example = "segredo123")
        String password
) {
}

package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais para login de operador")
public record LoginRequest(
        @Schema(description = "Nome de usuário do operador", example = "admin")
        String name,

        @Schema(description = "Senha do operador", example = "admin123")
        String password
) {
}

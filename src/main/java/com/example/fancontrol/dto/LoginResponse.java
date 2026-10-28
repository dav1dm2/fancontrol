package com.example.fancontrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de sucesso após autenticação com token JWT")
public record LoginResponse(
        @Schema(description = "Token de acesso JWT codificado em Base64", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,

        @Schema(description = "Tempo de expiração em segundos", example = "3600")
        long expiresIn,

        @Schema(description = "Dados do usuário autenticado")
        AuthenticatedUserResponse user
) {
}

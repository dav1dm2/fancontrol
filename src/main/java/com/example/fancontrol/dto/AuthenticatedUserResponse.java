package com.example.fancontrol.dto;

import com.example.fancontrol.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados do usuário autenticado")
public record AuthenticatedUserResponse(
        @Schema(description = "Identificador do usuário", example = "1")
        Long id,

        @Schema(description = "Nome do usuário", example = "admin")
        String name,

        @Schema(description = "Papel/nível de acesso do usuário", example = "ADMIN")
        Role role
) {
}

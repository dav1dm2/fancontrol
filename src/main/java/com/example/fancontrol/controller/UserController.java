package com.example.fancontrol.controller;

import com.example.fancontrol.config.OpenApiConfig;
import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.exception.ApiErrorResponse;
import com.example.fancontrol.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuários", description = "Endpoints administrativos para gerenciamento de operadores")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @Operation(summary = "Listar operadores", description = "Retorna o ID e o nome dos operadores cadastrados. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de operadores retornada com sucesso",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSummaryResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<UserSummaryResponse> listUsers() {
        return userManagementService.listUsers();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover operador", description = "Remove um operador pelo seu ID. Não permite remover o último administrador. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Operador removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Operador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Não é permitido remover o último administrador",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> removeUser(
            @Parameter(description = "Identificador do usuário a ser removido", example = "2")
            @PathVariable long id
    ) {
        userManagementService.removeUser(id);
        return ResponseEntity.noContent().build();
    }
}

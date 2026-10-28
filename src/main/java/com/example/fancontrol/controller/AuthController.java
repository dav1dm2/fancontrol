package com.example.fancontrol.controller;

import com.example.fancontrol.config.OpenApiConfig;
import com.example.fancontrol.dto.AuthenticatedUserResponse;
import com.example.fancontrol.dto.LoginRequest;
import com.example.fancontrol.dto.LoginResponse;
import com.example.fancontrol.dto.RegisterRequest;
import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.exception.ApiErrorResponse;
import com.example.fancontrol.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Endpoints para cadastro público, login de operadores e consulta de perfil")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Cadastrar novo operador", description = "Cria um novo operador no sistema com perfil padrão USER. Operação pública.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operador criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Nome ou senha inválidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome de usuário já cadastrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserSummaryResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Realizar login", description = "Autentica o operador com nome e senha, retornando um token JWT de acesso. Operação pública.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticação bem-sucedida com JWT"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciais incorretas",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @Operation(summary = "Consultar usuário autenticado", description = "Retorna os dados do operador associado ao token JWT enviado no cabeçalho Authorization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do usuário autenticado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado ou token inválido/expirado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.parseLong(jwt.getSubject()));
    }
}

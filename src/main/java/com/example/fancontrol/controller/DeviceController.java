package com.example.fancontrol.controller;

import com.example.fancontrol.config.OpenApiConfig;
import com.example.fancontrol.dto.DeviceRequest;
import com.example.fancontrol.dto.DeviceResponse;
import com.example.fancontrol.exception.ApiErrorResponse;
import com.example.fancontrol.mapper.DeviceMapper;
import com.example.fancontrol.model.Device;
import com.example.fancontrol.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "Dispositivos", description = "Endpoints para gerenciamento de dispositivos microcontrolados e envio de comandos")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;

    public DeviceController(DeviceService deviceService, DeviceMapper deviceMapper) {
        this.deviceService = deviceService;
        this.deviceMapper = deviceMapper;
    }

    @GetMapping
    @Operation(summary = "Listar dispositivos", description = "Retorna todos os dispositivos cadastrados. Requer perfil USER ou ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de dispositivos retornada com sucesso",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeviceResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<DeviceResponse> getDevices() {
        return deviceService.getDevices().stream().map(deviceMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar dispositivo por ID", description = "Retorna os detalhes de um dispositivo específico. Requer perfil USER ou ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispositivo encontrado",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispositivo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeviceResponse getDevice(
            @Parameter(description = "Identificador único do dispositivo", example = "1")
            @PathVariable int id
    ) {
        return deviceMapper.toResponse(deviceService.getDeviceById(id));
    }

    @PostMapping
    @Operation(summary = "Cadastrar dispositivo", description = "Cadastra um novo dispositivo microcontrolado. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispositivo criado com sucesso",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nome de dispositivo inválido ou em branco",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Dispositivo com este nome já existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DeviceResponse> addDevice(@RequestBody DeviceRequest request) {
        Device created = deviceService.addDevice(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dispositivo", description = "Atualiza o nome de um dispositivo existente, preservando status e estados MQTT. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispositivo atualizado com sucesso",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nome de dispositivo inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispositivo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Outro dispositivo já utiliza este nome",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeviceResponse updateDevice(
            @Parameter(description = "Identificador único do dispositivo", example = "1")
            @PathVariable int id,
            @RequestBody DeviceRequest request
    ) {
        return deviceMapper.toResponse(deviceService.updateDevice(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover dispositivo", description = "Remove um dispositivo do sistema. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dispositivo removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispositivo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> removeDevice(
            @Parameter(description = "Identificador único do dispositivo", example = "1")
            @PathVariable int id
    ) {
        deviceService.removeDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fan/on")
    @Operation(summary = "Ligar ventilador", description = "Envia comando MQTT para ligar o ventilador do dispositivo conectado. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comando enviado com sucesso",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispositivo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Dispositivo está OFFLINE ou ventilador já está ligado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Falha de comunicação com o broker MQTT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeviceResponse turnFanOn(
            @Parameter(description = "Identificador único do dispositivo", example = "1")
            @PathVariable int id
    ) {
        return deviceMapper.toResponse(deviceService.turnFanOn(id));
    }

    @PostMapping("/{id}/fan/off")
    @Operation(summary = "Desligar ventilador", description = "Envia comando MQTT para desligar o ventilador do dispositivo conectado. Requer perfil ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comando enviado com sucesso",
                    content = @Content(schema = @Schema(implementation = DeviceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer perfil ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Dispositivo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Dispositivo está OFFLINE ou ventilador já está desligado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Falha de comunicação com o broker MQTT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeviceResponse turnFanOff(
            @Parameter(description = "Identificador único do dispositivo", example = "1")
            @PathVariable int id
    ) {
        return deviceMapper.toResponse(deviceService.turnFanOff(id));
    }
}

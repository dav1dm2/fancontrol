package com.example.fancontrol.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI fanControlOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fan Control System API")
                        .description("API REST para monitoramento e controle de dispositivos microcontrolados de ventiladores (AV2). " +
                                "Operações de leitura requerem autenticação (USER ou ADMIN). " +
                                "Operações de alteração de dispositivos, envio de comandos e gestão de usuários requerem perfil ADMIN.")
                        .version("v2.0.0")
                        .contact(new Contact()
                                .name("Fan Control System")
                                .url("https://github.com/burnedrice/fancontrol")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Informe o token JWT obtido no endpoint /api/auth/login para autorizar as requisições.")));
    }
}

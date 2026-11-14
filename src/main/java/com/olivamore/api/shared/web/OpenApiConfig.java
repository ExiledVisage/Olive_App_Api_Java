package com.olivamore.api.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * API dokumantasyonunun basligi ve kimlik dogrulamasi: her endpoint Bearer JWT ister (giris gibi acik olanlar
 * Swagger'da da tokensiz calisir). Dokumantasyon yalnizca {@code springdoc.api-docs.enabled=true} iken uretilir
 * (dev profili).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
class OpenApiConfig {

	private static final String BEARER = "bearer-jwt";

	@Bean
	OpenAPI oliveOpenApi() {
		String version = Objects.requireNonNullElse(OpenApiConfig.class.getPackage().getImplementationVersion(), "dev");
		return new OpenAPI()
			.info(new Info().title("Olive API").version(version)
				.description("Olive uretim takibi API'si. Token: POST /api/auth/login"))
			.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
				.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}

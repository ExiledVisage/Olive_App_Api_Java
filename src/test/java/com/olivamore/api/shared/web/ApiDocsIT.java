package com.olivamore.api.shared.web;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/** API dokumantasyonu varsayilan olarak kapali; yalnizca dev profili acar (OliveApiApplicationIT). */
@ApiIntegrationTest
class ApiDocsIT {

	@LocalServerPort
	int port;

	@Test
	void apiDocsAndSwaggerUiAreClosedWithoutTheDevProfile() {
		Api api = Api.on(port).as(null);
		assertThat(api.get("/v3/api-docs").status()).isEqualTo(404);
		assertThat(api.get("/swagger-ui.html").status()).isEqualTo(404);
	}

}

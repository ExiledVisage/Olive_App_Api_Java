package com.olivamore.api.support;

import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Testlerde giris: token cevabinin bicimi tek yerde. Sifre seed'deki 123. */
public final class TestAuth {

	public static final String ACCESS_TOKEN = "access_token";
	public static final String REFRESH_TOKEN = "refresh_token";
	public static final String PASSWORD = "123";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private TestAuth() {
	}

	/** Giris cevabinin tamami. */
	public static JsonNode login(RestClient http, String email) {
		LaravelReplay.Response response = LaravelReplay.call(http, "POST", "/api/auth/login", null,
			JSON.createObjectNode().put("email", email).put("password", PASSWORD));
		if (response.status() != 200) {
			throw new IllegalStateException("Giris basarisiz: " + response.status() + " " + response.body());
		}
		return JSON.readTree(response.body());
	}

	public static String accessToken(RestClient http, String email) {
		return login(http, email).get(ACCESS_TOKEN).asString();
	}

}

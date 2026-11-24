package com.olivamore.api.devdata;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Demo akisinin API istemcisi: istekler calisan uygulamaya gider, her islem gercek kurallardan gecer. Her kullanici
 * bir kez giris yapar; access token suresi dolarsa (401) yeniden giris yapilip istek tekrarlanir.
 */
final class DemoApi {

	static final String PASSWORD = "123";

	private final RestClient http;
	private final JsonMapper json = JsonMapper.builder().build();
	private final Map<String, String> tokens = new HashMap<>();

	DemoApi(int port) {
		this.http = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	JsonNode post(String user, String path, Object body) {
		return call(user, HttpMethod.POST, path, body);
	}

	JsonNode get(String user, String path) {
		return call(user, HttpMethod.GET, path, null);
	}

	JsonNode patch(String user, String path, Object body) {
		return call(user, HttpMethod.PATCH, path, body);
	}

	JsonNode put(String user, String path, Object body) {
		return call(user, HttpMethod.PUT, path, body);
	}

	/** Cevaptaki {@code data.id}. */
	static long id(JsonNode response) {
		return response.get("data").get("id").asLong();
	}

	private JsonNode call(String user, HttpMethod method, String path, Object body) {
		Response response = send(method, path, token(user), body);
		if (response.status() == 401) {
			tokens.remove(user);
			response = send(method, path, token(user), body);
		}
		if (response.status() >= 300) {
			throw new IllegalStateException(method + " " + path + " (" + user + ") " + response.status() + ": "
				+ response.body() + "\nistek: " + json.writeValueAsString(body));
		}
		return response.body().isEmpty() ? json.createObjectNode() : json.readTree(response.body());
	}

	private String token(String user) {
		return tokens.computeIfAbsent(user, email -> {
			Response response = send(HttpMethod.POST, "/api/auth/login", null,
				Map.of("email", email, "password", PASSWORD));
			if (response.status() != 200) {
				throw new IllegalStateException("Demo kullanici giris yapamadi: " + email + " " + response.body());
			}
			return json.readTree(response.body()).get("access_token").asString();
		});
	}

	private Response send(HttpMethod method, String path, String token, Object body) {
		RestClient.RequestBodySpec spec = http.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			spec.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
		}
		return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
			new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private record Response(int status, String body) {
	}

}

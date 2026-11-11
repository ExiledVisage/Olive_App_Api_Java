package com.olivamore.api.support;

import com.olivamore.api.support.ApiClient.Response;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Kural testleri icin kisa HTTP yardimcisi. Varsayilan kullanici admin; {@link #as} baska kullanici icin bir
 * gorunum verir. Token'lar kullanici basina bir kez alinir (giris limiti dakikada 5).
 */
public final class Api {

	public static final String ADMIN = "admin@olivamore.com";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient http;
	private final Map<String, String> tokens;
	private final String user;

	private Api(RestClient http, Map<String, String> tokens, String user) {
		this.http = http;
		this.tokens = tokens;
		this.user = user;
	}

	public static Api on(int port) {
		return new Api(ApiClient.http(port), new HashMap<>(), ADMIN);
	}

	/** Ayni istemci ve token onbellegi, baska kullanici; null ise tokensiz. */
	public Api as(String email) {
		return new Api(http, tokens, email);
	}

	public Response get(String path) {
		return send("GET", path, null);
	}

	public Response delete(String path) {
		return send("DELETE", path, null);
	}

	/** @param body JSON metni ya da JSON'a cevrilecek nesne (Map, List, record) */
	public Response post(String path, Object body) {
		return send("POST", path, body);
	}

	public Response put(String path, Object body) {
		return send("PUT", path, body);
	}

	public Response patch(String path, Object body) {
		return send("PATCH", path, body);
	}

	/** {@code data.id} alanini dondurur; durum 2xx degilse hata. */
	public static long id(Response response) {
		return data(response).get("id").asLong();
	}

	public static JsonNode data(Response response) {
		if (response.status() >= 300) {
			throw new IllegalStateException("Beklenmeyen durum " + response.status() + ": " + response.body());
		}
		return response.json().get("data");
	}

	private Response send(String method, String path, Object body) {
		String token = user == null ? null : tokens.computeIfAbsent(user, email -> TestAuth.accessToken(http, email));
		return ApiClient.call(http, method, path, token, body == null ? null : toJson(body));
	}

	private static JsonNode toJson(Object body) {
		return body instanceof String text ? JSON.readTree(text) : JSON.valueToTree(body);
	}

}

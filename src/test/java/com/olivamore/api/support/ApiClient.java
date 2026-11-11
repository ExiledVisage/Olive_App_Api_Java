package com.olivamore.api.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** Testlerin HTTP istemcisi: yollar oldugu gibi gider (senaryo dosyalarindaki yollar zaten URL-kodlu). */
public final class ApiClient {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ApiClient() {
	}

	public record Response(int status, HttpHeaders headers, String body) {

		public JsonNode json() {
			return body.isEmpty() ? null : JSON.readTree(body);
		}

	}

	public static RestClient http(int port) {
		DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory("http://localhost:" + port);
		uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
		return RestClient.builder().uriBuilderFactory(uris).build();
	}

	/** @param body null ise govdesiz istek */
	public static Response call(RestClient http, String method, String path, String token, JsonNode body) {
		RestClient.RequestBodySpec spec = http.method(HttpMethod.valueOf(method)).uri(path)
			.accept(MediaType.APPLICATION_JSON);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null && !body.isNull()) {
			spec.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
		}
		return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
			response.getHeaders(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

}

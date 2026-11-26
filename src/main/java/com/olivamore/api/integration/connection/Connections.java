package com.olivamore.api.integration.connection;

import com.olivamore.api.integration.provider.Provider;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.integration.provider.ProviderClients;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Site ve pazar yeri baglantilari (docs/INTEGRATIONS.md 0): ekleme, guncelleme, silme, deneme. Erisim bilgileri
 * sifreli saklanir ve cevapta maskeli doner; guncellemede yalniz verilen bilgiler degisir.
 */
@Service
public class Connections {

	/** @param fields platformun istedigi erisim bilgileri (form icin) */
	record ProviderInfo(String key, String label, boolean supported, boolean needsBaseUrl,
		List<Provider.Field> fields) {
	}

	/** @param credentials maskeli ("ck_••••1a2b") */
	record View(long id, String provider, String providerLabel, String name, String baseUrl, String status,
		boolean orderPullEnabled, boolean stockPushEnabled, Map<String, String> credentials,
		LocalDateTime lastOrderSyncAt, LocalDateTime lastStockSyncAt, String lastError, LocalDateTime lastErrorAt,
		LocalDateTime createdAt) {
	}

	record CreateRequest(String provider, String name, String baseUrl, Map<String, String> credentials,
		Boolean orderPullEnabled, Boolean stockPushEnabled) {
	}

	/** Verilmeyen alan degismez; {@code credentials} icindeki bos olmayan bilgiler eskisinin yerine gecer. */
	record UpdateRequest(String name, String baseUrl, Map<String, String> credentials, String status,
		Boolean orderPullEnabled, Boolean stockPushEnabled) {
	}

	/** Diger alt paketlerin (eslestirme, siparis, stok) kullandigi cozulmus baglanti. */
	public record Connection(long id, Provider provider, String name, String baseUrl, boolean active,
		boolean orderPullEnabled, boolean stockPushEnabled, Map<String, String> credentials,
		LocalDateTime lastOrderSyncAt) {
	}

	private record ConnectionRow(long id, String provider, String name, String baseUrl, String credentialsEncrypted,
		String status, boolean orderPullEnabled, boolean stockPushEnabled, LocalDateTime lastOrderSyncAt,
		LocalDateTime lastStockSyncAt, String lastError, LocalDateTime lastErrorAt, LocalDateTime createdAt) {
	}

	private static final String SELECT = """
		select id, provider, name, base_url, credentials_encrypted, status, order_pull_enabled, stock_push_enabled,
			last_order_sync_at, last_stock_sync_at, last_error, last_error_at, created_at
		from integration_connections
		""";

	private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final CredentialCipher cipher;
	private final ProviderClients clients;
	private final JsonMapper json;
	private final boolean allowHttp;

	Connections(JdbcClient jdbc, CredentialCipher cipher, ProviderClients clients, JsonMapper json,
		@Value("${olive.integration.allow-http:false}") boolean allowHttp) {
		this.jdbc = jdbc;
		this.cipher = cipher;
		this.clients = clients;
		this.json = json;
		this.allowHttp = allowHttp;
	}

	List<ProviderInfo> providers() {
		return List.of(Provider.values()).stream()
			.map(p -> new ProviderInfo(p.key(), p.label(), p.supported(), p.needsBaseUrl(), p.fields())).toList();
	}

	@Transactional(readOnly = true)
	List<View> all() {
		return jdbc.sql(SELECT + " order by name, id").query(ConnectionRow.class).list().stream().map(this::view)
			.toList();
	}

	@Transactional(readOnly = true)
	View get(long id) {
		return view(row(id));
	}

	@Transactional
	View create(CreateRequest request, long userId) {
		ValidationErrors errors = new ValidationErrors();
		Provider provider = request.provider() == null ? null : Provider.of(request.provider()).orElse(null);
		if (provider == null) {
			errors.add("provider", MessageKeys.IN);
			errors.throwIfAny();
		}
		if (!provider.supported()) {
			throw new BusinessRuleException(provider.label() + " bağlantısı henüz desteklenmiyor.");
		}
		String name = blank(request.name());
		if (name == null) {
			errors.add("name", MessageKeys.REQUIRED);
		} else if (nameTaken(name, null)) {
			errors.add("name", MessageKeys.UNIQUE);
		}
		String baseUrl = baseUrl(provider, request.baseUrl(), errors);
		Map<String, String> credentials = credentials(provider, request.credentials(), Map.of(), errors);
		errors.throwIfAny();
		LocalDateTime now = Timestamps.now();
		long id = jdbc.sql("""
			insert into integration_connections (provider, name, base_url, credentials_encrypted, order_pull_enabled,
				stock_push_enabled, created_by_user_id, created_at, updated_at)
			values (:provider, :name, :base, :creds, :pull, :push, :user, :now, :now)
			returning id
			""").param("provider", provider.key()).param("name", name).param("base", baseUrl)
			.param("creds", cipher.encrypt(json.writeValueAsString(credentials)))
			.param("pull", request.orderPullEnabled() == null || request.orderPullEnabled())
			.param("push", request.stockPushEnabled() != null && request.stockPushEnabled())
			.param("user", userId).param("now", now).query(Long.class).single();
		return get(id);
	}

	@Transactional
	View update(long id, UpdateRequest request) {
		ConnectionRow row = row(id);
		Provider provider = Provider.of(row.provider()).orElseThrow();
		ValidationErrors errors = new ValidationErrors();
		String name = request.name() == null ? row.name() : blank(request.name());
		if (name == null) {
			errors.add("name", MessageKeys.REQUIRED);
		} else if (nameTaken(name, id)) {
			errors.add("name", MessageKeys.UNIQUE);
		}
		String baseUrl = request.baseUrl() == null ? row.baseUrl() : baseUrl(provider, request.baseUrl(), errors);
		Map<String, String> credentials = credentials(provider, request.credentials(), decrypt(row), errors);
		String status = request.status() == null ? row.status() : request.status();
		if (!List.of("active", "paused").contains(status)) {
			errors.add("status", MessageKeys.IN);
		}
		errors.throwIfAny();
		jdbc.sql("""
			update integration_connections set name = :name, base_url = :base, credentials_encrypted = :creds,
				status = :status, order_pull_enabled = :pull, stock_push_enabled = :push, updated_at = :now
			where id = :id
			""").param("name", name).param("base", baseUrl)
			.param("creds", cipher.encrypt(json.writeValueAsString(credentials))).param("status", status)
			.param("pull", request.orderPullEnabled() == null ? row.orderPullEnabled() : request.orderPullEnabled())
			.param("push", request.stockPushEnabled() == null ? row.stockPushEnabled() : request.stockPushEnabled())
			.param("now", Timestamps.now()).param("id", id).update();
		return get(id);
	}

	@Transactional
	void delete(long id) {
		row(id);
		jdbc.sql("delete from integration_connections where id = :id").param("id", id).update();
	}

	/** Erisim bilgileri ve adresle platforma bir istek atar; sonucu kaydetmez. */
	@Transactional(readOnly = true)
	ProviderClient.ConnectionCheck check(long id) {
		Connection connection = connection(id);
		return clients.create(connection.provider(), connection.baseUrl(), connection.credentials()).check();
	}

	/** Cozulmus baglanti: eslestirme, siparis ve stok isleri kullanir. */
	@Transactional(readOnly = true)
	public Connection connection(long id) {
		ConnectionRow row = row(id);
		return new Connection(row.id(), Provider.of(row.provider()).orElseThrow(), row.name(), row.baseUrl(),
			"active".equals(row.status()), row.orderPullEnabled(), row.stockPushEnabled(), decrypt(row),
			row.lastOrderSyncAt());
	}

	/** Bu baglantinin istemcisi. */
	public ProviderClient client(Connection connection) {
		return clients.create(connection.provider(), connection.baseUrl(), connection.credentials());
	}

	private View view(ConnectionRow row) {
		Provider provider = Provider.of(row.provider()).orElseThrow();
		Map<String, String> masked = new LinkedHashMap<>();
		Map<String, String> plain = decrypt(row);
		for (Provider.Field field : provider.fields()) {
			String value = plain.get(field.key());
			masked.put(field.key(), value == null ? null : field.secret() ? mask(value) : value);
		}
		return new View(row.id(), provider.key(), provider.label(), row.name(), row.baseUrl(), row.status(),
			row.orderPullEnabled(), row.stockPushEnabled(), masked, row.lastOrderSyncAt(), row.lastStockSyncAt(),
			row.lastError(), row.lastErrorAt(), row.createdAt());
	}

	/** Son dort karakter gorunur: "ck_••••1a2b". */
	static String mask(String value) {
		if (value.length() <= 4) {
			return "••••";
		}
		String prefix = value.startsWith("ck_") || value.startsWith("cs_") ? value.substring(0, 3) : "";
		return prefix + "••••" + value.substring(value.length() - 4);
	}

	private Map<String, String> decrypt(ConnectionRow row) {
		return json.readValue(cipher.decrypt(row.credentialsEncrypted()), MAP);
	}

	private ConnectionRow row(long id) {
		return jdbc.sql(SELECT + " where id = :id").param("id", id).query(ConnectionRow.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("IntegrationConnection", id));
	}

	private boolean nameTaken(String name, Long exceptId) {
		return jdbc.sql("""
			select count(*) from integration_connections
			where lower(name) = lower(:name) and (cast(:except as bigint) is null or id <> :except)
			""").param("name", name).param("except", exceptId).query(Long.class).single() > 0;
	}

	/**
	 * Site adresi: https (gelistirmede http'ye izin verilebilir), sondaki / atilir. Pazar yerinde istege bagli
	 * (bos: platformun canli adresi; test ortami icin verilir).
	 */
	private String baseUrl(Provider provider, String value, ValidationErrors errors) {
		String url = blank(value);
		if (url == null) {
			if (provider.needsBaseUrl()) {
				errors.add("base_url", MessageKeys.REQUIRED);
			}
			return null;
		}
		url = url.replaceAll("/+$", "");
		try {
			URI uri = new URI(url);
			boolean https = "https".equalsIgnoreCase(uri.getScheme());
			boolean http = "http".equalsIgnoreCase(uri.getScheme());
			if (uri.getHost() == null || !(https || (http && allowHttp))) {
				errors.add("base_url", "Site adresi https:// ile başlamalı (örn. https://magaza.com).");
			}
		} catch (URISyntaxException e) {
			errors.add("base_url", "Site adresi geçersiz.");
		}
		return url;
	}

	/** Zorunlu bilgiler tamam mi; bos gelen bilgi eskisini korur. */
	private Map<String, String> credentials(Provider provider, Map<String, String> given, Map<String, String> current,
		ValidationErrors errors) {
		Map<String, String> result = new LinkedHashMap<>(current);
		for (Provider.Field field : provider.fields()) {
			String value = given == null ? null : blank(given.get(field.key()));
			if (value != null) {
				result.put(field.key(), value);
			}
			if (result.get(field.key()) == null) {
				errors.add("credentials." + field.key(), MessageKeys.REQUIRED);
			}
		}
		result.keySet().retainAll(provider.fields().stream().map(Provider.Field::key).toList());
		return result;
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}

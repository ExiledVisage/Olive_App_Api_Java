package com.olivamore.api.apprelease;

import com.olivamore.api.identity.Public;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.throttle.Throttle;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Laravel {@code AppReleaseController}. Rate limit {@code throttle:30,1}: iki endpoint istemci basina
 * dakikada toplam 30 istegi paylasir.
 */
@RestController
@Public
@Throttle(maxAttempts = 30, decayMinutes = 1)
class AppReleaseController {

	static final String NOT_PUBLISHED = "Yayinlanmis surum yok.";
	static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");
	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	/** Symfony HeaderUtils::quote: bu karakterlerden olusan deger tirnaksiz yazilir. */
	private static final Pattern TOKEN = Pattern.compile("^[a-zA-Z0-9!#$%&'*.^_`|~-]+$");
	private static final Pattern PRINTABLE_ASCII = Pattern.compile("^[\\x20-\\x7e]*$");

	private final Path releases;
	/** PHP json_decode gibi: tamsayi Long, ondalik BigDecimal (1.0 -> (string) "1"). */
	private final JsonMapper json = JsonMapper.builder()
		.enable(DeserializationFeature.USE_LONG_FOR_INTS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	/**
	 * Yol metin olarak alinir: Spring'in Path donusturucusu goreli yolu ("../...", "releases")
	 * servlet kaynagi sanip reddediyor. Goreli yol calisma klasorune gore.
	 */
	AppReleaseController(@Value("${olive.releases-path}") String releases) {
		this.releases = Path.of(releases).toAbsolutePath().normalize();
	}

	@GetMapping("/api/app/version")
	DataResponse<Map<String, Object>> version() {
		Map<String, Object> manifest = manifest();
		if (manifest == null) {
			throw new HttpStatusException(404, NOT_PUBLISHED);
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("version_code", Php.intval(manifest.get("version_code")));
		data.put("version_name", Php.strval(manifest.get("version_name")));
		data.put("notes", manifest.get("notes"));
		data.put("sha256", manifest.get("sha256"));
		data.put("size", Php.intval(manifest.get("size")));
		data.put("published_at", manifest.get("published_at"));
		data.put("download_url", ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/app/download").toUriString());
		return DataResponse.of(data);
	}

	/**
	 * {@code response()->download($path, "olivamore-<surum>.apk", ['Content-Type' => ...])}: Symfony
	 * BinaryFileResponse. Range (206) ve HEAD Spring'in Resource destegiyle; Last-Modified ve
	 * Cache-Control dogrudan yazilir (ResponseEntity'de olsalar Spring If-Modified-Since'e 304 donerdi,
	 * Laravel donmuyor).
	 */
	@GetMapping("/api/app/download")
	ResponseEntity<Resource> download(HttpServletResponse response) throws IOException {
		Map<String, Object> manifest = manifest();
		Path apk = manifest == null ? null : path(manifest.containsKey("file") && manifest.get("file") != null
			? Php.strval(manifest.get("file")) : "olivamore.apk");
		if (apk == null || !Files.isRegularFile(apk)) {
			throw new HttpStatusException(404, NOT_PUBLISHED);
		}
		response.setHeader("Cache-Control", "public");
		response.setHeader("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
			.format(Files.getLastModifiedTime(apk).toInstant()));
		response.setHeader("Content-Disposition", disposition("olivamore-" + Php.strval(manifest.get("version_name")) + ".apk"));
		return ResponseEntity.ok().contentType(APK).body(new FileSystemResource(apk));
	}

	/** {@code version.json}; yoksa, bozuksa ya da version_code / version_name yoksa null (Laravel: isset). */
	@SuppressWarnings("unchecked")
	private Map<String, Object> manifest() {
		Path file = path("version.json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			byte[] bytes = Files.readAllBytes(file);
			// PHP json_decode BOM'u kabul etmez; Jackson atlardi
			if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
				return null;
			}
			Object data = json.readValue(new String(bytes, StandardCharsets.UTF_8), Object.class);
			if (!(data instanceof Map<?, ?> map) || map.get("version_code") == null || map.get("version_name") == null) {
				return null;
			}
			return (Map<String, Object>) map;
		}
		catch (IOException | RuntimeException broken) {
			return null;
		}
	}

	/** Klasor disina cikilmasin: yalnizca dosya adi ({@code basename}). */
	private Path path(String name) {
		Path resolved = releases.resolve(Php.basename(name)).normalize();
		return resolved.startsWith(releases.normalize()) && !resolved.equals(releases.normalize()) ? resolved : releases.resolve(".invalid");
	}

	/**
	 * Laravel ResponseFactory::download + Symfony HeaderUtils::makeDisposition:
	 * ASCII disi adda yedek ad ({@code Str::ascii}, % atilir) + {@code filename*=utf-8''...}.
	 * Adda "/" ya da "\" varsa Symfony hata verir (500).
	 */
	static String disposition(String filename) {
		String fallback = Php.ascii(filename).replace("%", "");
		if (!PRINTABLE_ASCII.matcher(fallback).matches()) {
			throw new IllegalArgumentException("The filename fallback must only contain ASCII characters.");
		}
		if (filename.contains("/") || filename.contains("\\") || fallback.contains("/") || fallback.contains("\\")) {
			throw new IllegalArgumentException("The filename and the fallback cannot contain the \"/\" and \"\\\" characters.");
		}
		StringBuilder header = new StringBuilder("attachment; filename=").append(quote(fallback));
		if (!filename.equals(fallback)) {
			header.append("; filename*=").append(quote("utf-8''" + rawUrlEncode(filename)));
		}
		return header.toString();
	}

	private static String quote(String value) {
		if (TOKEN.matcher(value).matches()) {
			return value;
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** PHP rawurlencode */
	private static String rawUrlEncode(String value) {
		StringBuilder out = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xFF;
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
				out.append((char) c);
			}
			else {
				out.append('%').append(String.format("%02X", c));
			}
		}
		return out.toString();
	}

}

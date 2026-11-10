package com.olivamore.api.apprelease;

import com.olivamore.api.identity.Public;
import com.olivamore.api.shared.error.HttpStatusException;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Uygulama surumu ve APK indirme. Iki endpoint istemci basina dakikada toplam 30 istegi paylasir. */
@RestController
@Public
@Throttle(maxAttempts = 30, decayMinutes = 1)
class AppReleaseController {

	static final String NOT_PUBLISHED = "Yayinlanmis surum yok.";
	static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");
	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	/** Bu karakterlerden olusan deger tirnaksiz yazilir. */
	private static final Pattern TOKEN = Pattern.compile("^[a-zA-Z0-9!#$%&'*.^_`|~-]+$");
	private static final Pattern PRINTABLE_ASCII = Pattern.compile("^[\\x20-\\x7e]*$");
	/** Metnin bastaki sayisal kismi ({@code "7abc"} icin 7). */
	private static final Pattern LEADING_NUMBER =
		Pattern.compile("^[ \\t\\n\\r\\x0B\\f]*([+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?)");

	private final Path releases;
	/** Tamsayi Long, ondalik BigDecimal. */
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
	DataResponse<AppVersionResponse> version() {
		Map<String, Object> manifest = manifest();
		if (manifest == null) {
			throw new HttpStatusException(404, NOT_PUBLISHED);
		}
		return DataResponse.of(new AppVersionResponse(integer(manifest.get("version_code")),
			text(manifest.get("version_name")), manifest.get("notes"), manifest.get("sha256"),
			integer(manifest.get("size")), manifest.get("published_at"),
			ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/app/download").toUriString()));
	}

	/**
	 * Range (206) ve HEAD Spring'in Resource destegiyle; Last-Modified ve Cache-Control dogrudan yazilir
	 * (ResponseEntity'de olsalar Spring If-Modified-Since'e 304 donerdi).
	 */
	@GetMapping("/api/app/download")
	ResponseEntity<Resource> download(HttpServletResponse response) throws IOException {
		Map<String, Object> manifest = manifest();
		Path apk = manifest == null ? null
			: path(manifest.get("file") != null ? text(manifest.get("file")) : "olivamore.apk");
		if (apk == null || !Files.isRegularFile(apk)) {
			throw new HttpStatusException(404, NOT_PUBLISHED);
		}
		response.setHeader("Cache-Control", "public");
		response.setHeader("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
			.format(Files.getLastModifiedTime(apk).toInstant()));
		response.setHeader("Content-Disposition", disposition("olivamore-" + text(manifest.get("version_name")) + ".apk"));
		return ResponseEntity.ok().contentType(APK).body(new FileSystemResource(apk));
	}

	/** {@code version.json}; yoksa, bozuksa ya da version_code / version_name yoksa null. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> manifest() {
		Path file = path("version.json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			byte[] bytes = Files.readAllBytes(file);
			// BOM'lu dosya gecersiz sayilir; Jackson atlardi
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

	/** Klasor disina cikilmasin: yalnizca dosya adi. */
	private Path path(String name) {
		Path resolved = releases.resolve(baseName(name)).normalize();
		return resolved.startsWith(releases) && !resolved.equals(releases) ? resolved : releases.resolve(".invalid");
	}

	private static String baseName(String path) {
		String trimmed = path;
		while (trimmed.length() > 1 && trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.substring(trimmed.lastIndexOf('/') + 1);
	}

	/** Dosya elle duzenlenebilir: sayi yerine metin ya da boolean yazilmis olabilir. */
	private static long integer(Object value) {
		return switch (value) {
			case null -> 0;
			case Long number -> number;
			case BigInteger number -> clamp(number);
			case BigDecimal number -> clamp(number.toBigInteger());
			case Boolean flag -> flag ? 1 : 0;
			case String string -> {
				Matcher number = LEADING_NUMBER.matcher(string);
				yield number.find() ? clamp(new BigDecimal(number.group(1)).toBigInteger()) : 0;
			}
			case Collection<?> items -> items.isEmpty() ? 0 : 1;
			case Map<?, ?> entries -> entries.isEmpty() ? 0 : 1;
			default -> 0;
		};
	}

	private static long clamp(BigInteger number) {
		return number.bitLength() < 64 ? number.longValue() : (number.signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE);
	}

	private static String text(Object value) {
		return switch (value) {
			case null -> "";
			case Boolean flag -> flag ? "1" : "";
			case BigDecimal number -> number.stripTrailingZeros().scale() <= 0 ? number.toBigInteger().toString()
				: number.stripTrailingZeros().toPlainString();
			case Collection<?> items -> "Array";
			case Map<?, ?> entries -> "Array";
			default -> value.toString();
		};
	}

	/** ASCII disi adda yedek ad + {@code filename*=utf-8''...}. Adda "/" ya da "\" varsa hata. */
	static String disposition(String filename) {
		String fallback = ascii(filename).replace("%", "");
		if (!PRINTABLE_ASCII.matcher(fallback).matches()) {
			throw new IllegalArgumentException("The filename fallback must only contain ASCII characters.");
		}
		if (filename.contains("/") || filename.contains("\\") || fallback.contains("/") || fallback.contains("\\")) {
			throw new IllegalArgumentException("The filename and the fallback cannot contain the \"/\" and \"\\\" characters.");
		}
		StringBuilder header = new StringBuilder("attachment; filename=").append(quote(fallback));
		if (!filename.equals(fallback)) {
			header.append("; filename*=").append(quote("utf-8''" + percentEncode(filename)));
		}
		return header.toString();
	}

	/** Aksanlar kaldirilir, Turkce ve birkac Latin harf karsiligina cevrilir, kalan ASCII disi karakter atilir. */
	private static String ascii(String value) {
		StringBuilder mapped = new StringBuilder();
		for (char c : value.toCharArray()) {
			mapped.append(switch (c) {
				case 'ı' -> "i";
				case 'İ' -> "I";
				case 'ß' -> "ss";
				case 'æ' -> "ae";
				case 'Æ' -> "AE";
				case 'ø' -> "o";
				case 'Ø' -> "O";
				case 'ł' -> "l";
				case 'Ł' -> "L";
				case 'đ' -> "d";
				case 'Đ' -> "D";
				default -> String.valueOf(c);
			});
		}
		StringBuilder out = new StringBuilder();
		Normalizer.normalize(mapped, Normalizer.Form.NFD).codePoints().filter(cp -> cp < 0x80).forEach(out::appendCodePoint);
		return out.toString();
	}

	private static String quote(String value) {
		if (TOKEN.matcher(value).matches()) {
			return value;
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String percentEncode(String value) {
		StringBuilder out = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xFF;
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_'
				|| c == '.' || c == '~') {
				out.append((char) c);
			}
			else {
				out.append('%').append(String.format("%02X", c));
			}
		}
		return out.toString();
	}

}

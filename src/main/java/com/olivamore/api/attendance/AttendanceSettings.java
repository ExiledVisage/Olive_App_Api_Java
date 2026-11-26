package com.olivamore.api.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Giris-cikis ayarlari ({@code olive.attendance.*}). Zaman kolonlari UTC; gun ve plan saatleri fabrikanin yerel
 * saatiyle ({@link #zone()}) hesaplanir.
 */
@Component
class AttendanceSettings {

	private final ZoneId zone;
	private final String codeSecret;
	private final Duration codeTtl;
	private final int roundingMinutes;
	private final Duration duplicateWindow;
	private final Duration forgottenAfter;

	/**
	 * @param zone            fabrikanin saat dilimi
	 * @param codeSecret      QR imzasinin anahtari (bos ise surec basina rastgele; yeniden baslayinca kodlar yenilenir)
	 * @param codeTtl         QR'in gecerlilik suresi
	 * @param roundingMinutes fazla mesai bu dakikalik dilimlere asagi yuvarlanir (0: yuvarlama yok)
	 * @param duplicateWindow ayni kisinin bu sure icindeki ikinci okutmasi yok sayilir
	 * @param forgottenAfter  bundan eski acik giris unutulmus sayilir (cikis yok), yeni okutma giris olur
	 */
	AttendanceSettings(@Value("${olive.attendance.zone:Europe/Istanbul}") String zone,
		@Value("${olive.attendance.code-secret:}") String codeSecret,
		@Value("${olive.attendance.code-ttl:60s}") Duration codeTtl,
		@Value("${olive.attendance.rounding-minutes:15}") int roundingMinutes,
		@Value("${olive.attendance.duplicate-window:2m}") Duration duplicateWindow,
		@Value("${olive.attendance.forgotten-after:16h}") Duration forgottenAfter) {
		this.zone = ZoneId.of(zone);
		this.codeSecret = codeSecret;
		this.codeTtl = codeTtl;
		this.roundingMinutes = roundingMinutes;
		this.duplicateWindow = duplicateWindow;
		this.forgottenAfter = forgottenAfter;
	}

	ZoneId zone() {
		return zone;
	}

	String codeSecret() {
		return codeSecret;
	}

	Duration codeTtl() {
		return codeTtl;
	}

	int roundingMinutes() {
		return roundingMinutes;
	}

	Duration duplicateWindow() {
		return duplicateWindow;
	}

	Duration forgottenAfter() {
		return forgottenAfter;
	}

	/** UTC kolon degeri -> yerel saat. */
	LocalDateTime local(LocalDateTime utc) {
		return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
	}

	/** Yerel saat -> UTC kolon degeri. */
	LocalDateTime utc(LocalDateTime local) {
		return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	/** Yerel gunun basi, UTC. */
	LocalDateTime dayStartUtc(LocalDate day) {
		return utc(day.atStartOfDay());
	}

}

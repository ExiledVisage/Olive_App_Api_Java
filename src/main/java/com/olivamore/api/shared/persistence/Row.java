package com.olivamore.api.shared.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Bir tablo satiri (Eloquent modelinin attribute'lari). Kolon sirasi tablodaki sira.
 * Degerler {@link Db}'nin donusturdugu Java tipleridir: Long/Integer, BigDecimal, Boolean,
 * String, LocalDateTime, JSON kolonlarda cozulmus deger (Map/List/...).
 */
public record Row(Map<String, Object> values) {

	public Row {
		values = Collections.unmodifiableMap(values);
	}

	public Object get(String column) {
		return values.get(column);
	}

	public long id() {
		return ((Number) values.get("id")).longValue();
	}

	public Long longValue(String column) {
		Object v = values.get(column);
		return v == null ? null : ((Number) v).longValue();
	}

	public Integer intValue(String column) {
		Object v = values.get(column);
		return v == null ? null : ((Number) v).intValue();
	}

	public String string(String column) {
		Object v = values.get(column);
		return v == null ? null : v.toString();
	}

	public Boolean bool(String column) {
		return (Boolean) values.get(column);
	}

	public BigDecimal decimal(String column) {
		return (BigDecimal) values.get(column);
	}

	public LocalDateTime time(String column) {
		return (LocalDateTime) values.get(column);
	}

}

package com.olivamore.api.shared.persistence;

import com.olivamore.api.shared.php.Php;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Eager loading yardimcilari: anahtar toplama, id'ye gore eslestirme, gruplama. */
public final class Relations {

	private Relations() {
	}

	public static List<Long> ids(Collection<Row> rows) {
		return rows.stream().map(Row::id).toList();
	}

	/** Bir kolonun (null olmayan, tekrarsiz) degerleri; belongsTo yuklemesinin anahtarlari. */
	public static List<Long> column(Collection<Row> rows, String column) {
		Set<Long> out = new LinkedHashSet<>();
		for (Row row : rows) {
			Object v = row.get(column);
			if (v != null) {
				out.add(Php.intval(v));
			}
		}
		return new ArrayList<>(out);
	}

	public static Map<Long, Row> byId(List<Row> rows) {
		return rows.stream().collect(Collectors.toMap(Row::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
	}

	/** hasMany: satirlar yabanci anahtara gore, sorgu sirasi korunarak. */
	public static Map<Long, List<Row>> groupBy(List<Row> rows, String foreignKey) {
		Map<Long, List<Row>> out = new LinkedHashMap<>();
		for (Row row : rows) {
			Object key = row.get(foreignKey);
			if (key != null) {
				out.computeIfAbsent(Php.intval(key), k -> new ArrayList<>()).add(row);
			}
		}
		return out;
	}

	/** Laravel'in whereIn bicimi: {@code 1, 2, 3} (tamsayi anahtarlar sorguya yazilir). */
	public static String inList(Collection<Long> keys) {
		return keys.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(", "));
	}

	public static Long key(Object value) {
		return value == null ? null : Php.intval(value);
	}

}

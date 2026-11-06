package com.olivamore.api.shared.persistence;

import com.olivamore.api.shared.php.Php;
import com.olivamore.api.shared.time.Timestamps;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Eloquent'in veritabani davranisinin JDBC karsiligi (Laravel tablolarina birebir yazmak icin):
 * <ul>
 *   <li>Degerler PDO gibi tipsiz metin olarak gonderilir (PostgreSQL "unknown"); tipi sunucu kolondan
 *       belirler: {@code "1"} boolean kolona true, {@code "5"} bigint kolona 5 olarak girer.</li>
 *   <li>{@link #insert}: yalnizca verilen kolonlar + created_at/updated_at ({@code Model::create})</li>
 *   <li>{@link #update}: yalnizca degisen kolonlar; bir sey degistiyse updated_at ({@code $model->update})</li>
 *   <li>{@link #whereIn}: iliski yukleme sorgusu Laravel'inkiyle ayni bicimde
 *       ({@code where col in (1, 2, 3)}): siralanmayan ya da esit siralama degerli iliskilerde
 *       PostgreSQL ayni sorguya ayni satir sirasini verir</li>
 *   <li>JSON kolonlar ({@code 'array'} cast) okurken cozulur, yazarken {@link Json} ile isaretlenir</li>
 * </ul>
 */
@Component
public class Db {

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** PHP json_decode gibi: tamsayi Long, ondalik BigDecimal. */
	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.USE_LONG_FOR_INTS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	/** JSON kolona yazilacak deger ({@code 'array'} cast): null -> NULL, digerleri json_encode. */
	public record Json(Object value) {
	}

	private final JdbcTemplate jdbc;

	public Db(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public static Json json(Object value) {
		return new Json(value);
	}

	// ---- okuma -------------------------------------------------------------

	public List<Row> select(String sql, Object... args) {
		return jdbc.query(sql, bind(List.of(args)), (rs, n) -> row(rs));
	}

	public Optional<Row> first(String sql, Object... args) {
		List<Row> rows = select(sql, args);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
	}

	public Optional<Row> find(String table, long id) {
		return first("select * from " + identifier(table) + " where " + identifier(table) + ".id = ? limit 1", id);
	}

	/**
	 * Eloquent eager loading: {@code select * from t where t.col in (1, 2, 3) [order by ...]}.
	 * Anahtar yoksa sorgu atilmaz.
	 */
	public List<Row> whereIn(String table, String column, Collection<Long> keys, String orderBy) {
		LinkedHashSet<Long> unique = new LinkedHashSet<>();
		keys.stream().filter(Objects::nonNull).forEach(unique::add);
		if (unique.isEmpty()) {
			return List.of();
		}
		String t = identifier(table);
		String sql = "select * from " + t + " where " + t + "." + identifier(column) + " in ("
			+ unique.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")"
			+ (orderBy == null ? "" : " order by " + orderBy);
		return select(sql);
	}

	public long count(String sql, Object... args) {
		Long n = jdbc.query(sql, bind(List.of(args)), rs -> rs.next() ? rs.getLong(1) : 0L);
		return n == null ? 0 : n;
	}

	// ---- yazma -------------------------------------------------------------

	/**
	 * {@code Model::create($values)} sonucu donen model: veritabanindan okunmaz, yalnizca verilen degerler +
	 * updated_at, created_at, id. Kolon varsayilanlari (DB default) bu yuzden gorunmez (null).
	 */
	public Row create(String table, Map<String, ?> values) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		values.forEach((k, v) -> attributes.put(k, v instanceof Json j ? j.value() : v));
		LocalDateTime now = Timestamps.now();
		Map<String, Object> columns = new LinkedHashMap<>(values);
		columns.put("updated_at", now);
		columns.put("created_at", now);
		long id = insert(table, columns, false);
		attributes.put("updated_at", now);
		attributes.put("created_at", now);
		attributes.put("id", id);
		return new Row(attributes);
	}

	/** {@code Model::create($values)}; created_at/updated_at eklenir. Yeni id doner. */
	public long insert(String table, Map<String, ?> values) {
		return insert(table, values, true);
	}

	public long insert(String table, Map<String, ?> values, boolean timestamps) {
		Map<String, Object> columns = new LinkedHashMap<>(values);
		if (timestamps) {
			LocalDateTime now = Timestamps.now();
			columns.put("updated_at", now);
			columns.put("created_at", now);
		}
		String names = columns.keySet().stream().map(Db::identifier).collect(Collectors.joining(", "));
		String marks = columns.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
		Long id = jdbc.query("insert into " + identifier(table) + " (" + names + ") values (" + marks + ") returning id",
			bind(new ArrayList<>(columns.values())), rs -> rs.next() ? rs.getLong(1) : null);
		return Objects.requireNonNull(id);
	}

	/**
	 * {@code $model->update($values)}: yalnizca degisen kolonlar yazilir, bir sey degistiyse updated_at da.
	 * @return bir sey degisti mi
	 */
	public boolean update(String table, long id, Map<String, ?> values) {
		Row current = find(table, id).orElseThrow();
		Map<String, Object> dirty = new LinkedHashMap<>();
		values.forEach((column, value) -> {
			if (!equivalent(current.get(column), value)) {
				dirty.put(column, value);
			}
		});
		if (dirty.isEmpty()) {
			return false;
		}
		if (current.values().containsKey("updated_at")) {
			dirty.put("updated_at", Timestamps.now());
		}
		String set = dirty.keySet().stream().map(c -> identifier(c) + " = ?").collect(Collectors.joining(", "));
		List<Object> args = new ArrayList<>(dirty.values());
		args.add(id);
		jdbc.update("update " + identifier(table) + " set " + set + " where id = ?", bind(args));
		return true;
	}

	public int execute(String sql, Object... args) {
		return jdbc.update(sql, bind(List.of(args)));
	}

	public int delete(String table, long id) {
		return execute("delete from " + identifier(table) + " where id = ?", id);
	}

	// ---- donusumler --------------------------------------------------------

	/** PDO gibi: her deger tipsiz metin (Types.OTHER), JSON degerler json_encode. */
	private static PreparedStatementSetter bind(List<Object> args) {
		return ps -> {
			for (int i = 0; i < args.size(); i++) {
				Object v = args.get(i);
				if (v instanceof Json j) {
					v = j.value() == null ? null : JSON.writeValueAsString(j.value());
				}
				if (v == null) {
					ps.setNull(i + 1, Types.OTHER);
				}
				else if (v instanceof Boolean b) {
					// PostgresConnection::bindValues booleanlari PDO::PARAM_BOOL ile baglar
					ps.setObject(i + 1, b ? "t" : "f", Types.OTHER);
				}
				else if (v instanceof LocalDateTime t) {
					ps.setObject(i + 1, SQL_TIME.format(t), Types.OTHER);
				}
				else {
					ps.setObject(i + 1, Php.strval(v), Types.OTHER);
				}
			}
		};
	}

	private static Row row(ResultSet rs) throws SQLException {
		ResultSetMetaData meta = rs.getMetaData();
		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			values.put(meta.getColumnLabel(i), convert(rs.getObject(i)));
		}
		return new Row(values);
	}

	private static Object convert(Object value) {
		return switch (value) {
			case Timestamp t -> t.toLocalDateTime();
			case PGobject o when ("json".equals(o.getType()) || "jsonb".equals(o.getType())) ->
				o.getValue() == null ? null : JSON.readValue(o.getValue(), Object.class);
			case PGobject o -> o.getValue();
			case null, default -> value;
		};
	}

	/** Eloquent {@code originalIsEquivalent}: sayilar sayisal, JSON cozulmus, digerleri metin olarak. */
	private static boolean equivalent(Object current, Object next) {
		Object value = next instanceof Json j ? j.value() : next;
		if (current == null || value == null) {
			return current == null && value == null;
		}
		if (current instanceof Boolean b) {
			return b == truthy(value);
		}
		if (isNumber(current) && isNumber(value)) {
			return new BigDecimal(Php.strval(current)).compareTo(new BigDecimal(Php.strval(value).strip())) == 0;
		}
		if (next instanceof Json) {
			return JSON.valueToTree(current).equals(JSON.valueToTree(value));
		}
		if (current instanceof LocalDateTime t && value instanceof LocalDateTime u) {
			return t.equals(u);
		}
		return Php.strval(current).equals(Php.strval(value));
	}

	private static boolean truthy(Object v) {
		return switch (v) {
			case Boolean b -> b;
			case String s -> !s.isEmpty() && !s.equals("0") && !s.equalsIgnoreCase("false");
			case Number n -> new BigDecimal(n.toString()).signum() != 0;
			default -> true;
		};
	}

	private static boolean isNumber(Object v) {
		return v instanceof Number || (v instanceof String s && s.strip().matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)"));
	}

	static String identifier(String name) {
		if (!IDENTIFIER.matcher(name).matches()) {
			throw new IllegalArgumentException("Gecersiz tablo/kolon adi: " + name);
		}
		return name;
	}

}

package com.olivamore.api.devdata;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo akisinin bildigi tanimlar: tanimlar sabit veriden ({@code master-data.sql}) kod ve anahtarla bulunur, akis id
 * ezberlemez. Bir kez okunur.
 */
final class Ids {

	private static final BigDecimal TOLERANCE = new BigDecimal("0.02");
	/** Tarif asitlik sinirina bu kadar yaklasan tanktan dolum yapilmaz (yuvarlama payi). */
	private static final BigDecimal ACIDITY_MARGIN = new BigDecimal("0.005");

	private final JdbcTemplate jdbc;
	private final Map<String, Long> tanks = new LinkedHashMap<>();
	private final Map<String, Long> types = new LinkedHashMap<>();
	private final Map<String, Long> fields = new LinkedHashMap<>();
	private final List<Long> lines;
	private final List<SupplierField> suppliers;
	private final Map<String, ProductSupplier> productSuppliers = new LinkedHashMap<>();
	private final Map<Long, String> packagingItems = new LinkedHashMap<>();
	private final List<Customer> customers;
	private final List<Product> products;
	private final List<Source> sources;

	Ids(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		jdbc.query("select code, id from storage_units order by id", rs -> {
			tanks.put(rs.getString("code"), rs.getLong("id"));
		});
		jdbc.query("select code, id from production_product_types order by id", rs -> {
			types.put(rs.getString("code"), rs.getLong("id"));
		});
		jdbc.query("select field_key, id from process_type_fields order by id", rs -> {
			fields.put(rs.getString("field_key"), rs.getLong("id"));
		});
		lines = jdbc.queryForList("select id from production_lines order by id", Long.class);
		suppliers = jdbc.query("""
			select s.id as supplier_id, (select min(f.id) from supplier_fields f where f.supplier_id = s.id and f.is_active)
				as field_id
			from suppliers s where s.is_active order by s.id
			""", (rs, i) -> new SupplierField(rs.getLong("supplier_id"), (Long) rs.getObject("field_id")));
		jdbc.query("select id, name, code from product_suppliers where is_active order by id", rs -> {
			productSuppliers.put(rs.getString("code"), new ProductSupplier(rs.getLong("id"), initials(rs.getString("name"))));
		});
		jdbc.query("select id, type from packaging_items where is_active order by id", rs -> {
			packagingItems.put(rs.getLong("id"), rs.getString("type"));
		});
		customers = jdbc.query("""
			select c.id, a.id as address_id from customers c
			join customer_addresses a on a.customer_id = c.id and a.is_default
			where c.is_active order by c.id
			""", (rs, i) -> new Customer(rs.getLong("id"), rs.getLong("address_id")));
		products = loadProducts();
		sources = loadSources();
	}

	long tank(String code) {
		return require(tanks, code);
	}

	long type(String code) {
		return require(types, code);
	}

	long field(String key) {
		return require(fields, key);
	}

	/** n. uretim hatti (1'den baslar). */
	long line(int n) {
		return lines.get(n - 1);
	}

	List<Long> stages(long line) {
		return jdbc.queryForList("select id from production_stages where production_line_id = ? order by stage_order",
			Long.class, line);
	}

	long pomaceProduct() {
		return jdbc.queryForObject("select id from production_products where key = 'pomace'", Long.class);
	}

	/** Urun tipinin asitlik tanimi (urune bagli ozellik). */
	long acidityDefinition(long typeId) {
		return jdbc.queryForObject("""
			select d.id from production_product_attribute_definitions d
			join attribute_definitions a on a.id = d.attribute_definition_id and a.attribute_key = 'acidity'
			join production_product_types t on t.production_product_id = d.production_product_id and t.id = ?
			""", Long.class, typeId);
	}

	List<SupplierField> suppliers() {
		return suppliers;
	}

	/** Ambalaj kalemleri ve turleri (BOTTLE, CAP, LABEL). */
	Map<Long, String> packagingItems() {
		return packagingItems;
	}

	/** Ambalaj turunun tedarikcisi: sise camcidan, kapak kapakcidan, etiket matbaadan. */
	ProductSupplier productSupplier(String packagingType) {
		String code = switch (packagingType) {
			case "BOTTLE" -> "TED-CAM-01";
			case "CAP" -> "TED-KAP-01";
			default -> "TED-ETK-01";
		};
		ProductSupplier supplier = productSuppliers.get(code);
		if (supplier == null) {
			throw new IllegalStateException("Demo tanim verisinde yok: " + code);
		}
		return supplier;
	}

	/** Adin ilk uc kelimesinin bas harfleri: Ege Cam Ambalaj A.S. -> ECA. */
	private static String initials(String name) {
		StringBuilder out = new StringBuilder();
		for (String word : name.split(" ")) {
			if (out.length() < 3 && !word.isEmpty()) {
				out.append(Character.toUpperCase(word.charAt(0)));
			}
		}
		return out.toString();
	}

	List<Customer> customers() {
		return customers;
	}

	List<Product> products() {
		return products;
	}

	/** Dolum yapilan tanklar: toplama ve rezerv tanklari disindaki son tanklar. */
	List<Source> sources() {
		return sources;
	}

	private List<Product> loadProducts() {
		long cold = jdbc.queryForObject("select id from finished_product_storages where name = 'Soğuk Depo'", Long.class);
		long shipping = jdbc.queryForObject("select id from finished_product_storages where name = 'Sevkiyat Deposu'",
			Long.class);
		long orange = type("ORANGE");
		List<Product> list = new ArrayList<>();
		jdbc.query("select id, finished_product_recipe_id, volume_litre from finished_products where is_active order by id",
			rs -> {
				long id = rs.getLong("id");
				long recipe = rs.getLong("finished_product_recipe_id");
				BigDecimal volume = rs.getBigDecimal("volume_litre").setScale(4, RoundingMode.HALF_UP);
				Map<Long, Integer> components = new LinkedHashMap<>();
				jdbc.query("select packaging_item_id, quantity from finished_product_components "
					+ "where finished_product_id = ? order by id", r -> {
						components.merge(r.getLong("packaging_item_id"), r.getInt("quantity"), Integer::sum);
					}, id);
				Integer flavoured = jdbc.queryForObject("select count(*) from finished_product_recipe_contents "
					+ "where finished_product_recipe_id = ? and production_product_type_id = ?", Integer.class, recipe, orange);
				int[] limits = limits(volume);
				list.add(new Product(id, recipe, volume, components, flavoured > 0 ? cold : shipping, limits[0], limits[1]));
			});
		return list;
	}

	/** Hacme gore bir kosunun en az ve en cok sise adedi. */
	private static int[] limits(BigDecimal volume) {
		if (volume.compareTo(BigDecimal.ONE) > 0) {
			return new int[] {20, 400};
		}
		if (volume.compareTo(new BigDecimal("0.5")) > 0) {
			return new int[] {60, 800};
		}
		return new int[] {100, volume.compareTo(new BigDecimal("0.25")) > 0 ? 1500 : 2000};
	}

	private List<Source> loadSources() {
		List<Recipe> recipes = new ArrayList<>();
		jdbc.query("select id from finished_product_recipes where is_active order by id", rs -> {
			long id = rs.getLong("id");
			Map<Long, BigDecimal> contents = new LinkedHashMap<>();
			jdbc.query("select production_product_type_id, ratio from finished_product_recipe_contents "
				+ "where finished_product_recipe_id = ? order by id", r -> {
					contents.put(r.getLong("production_product_type_id"), r.getBigDecimal("ratio"));
				}, id);
			BigDecimal[] acidity = {null, null};
			jdbc.query("""
				select r.value, r.min_deviation, r.max_deviation from finished_product_recipe_attribute_rules r
				join attribute_definitions a on a.id = r.attribute_definition_id and a.attribute_key = 'acidity'
				where r.finished_product_recipe_id = ?
				""", r -> {
					BigDecimal value = r.getBigDecimal("value");
					BigDecimal min = r.getBigDecimal("min_deviation");
					BigDecimal max = r.getBigDecimal("max_deviation");
					acidity[0] = min == null ? null : value.subtract(min).add(ACIDITY_MARGIN);
					acidity[1] = max == null ? null : value.add(max).subtract(ACIDITY_MARGIN);
				}, id);
			List<Product> recipeProducts = products.stream().filter(p -> p.recipe() == id).toList();
			if (!recipeProducts.isEmpty()) {
				recipes.add(new Recipe(id, contents, acidity[0], acidity[1], recipeProducts));
			}
		});
		return jdbc.queryForList("""
			select id from storage_units where kind = 'final' and is_active
			and code not like 'TOPLAMA-%' and code not like 'REZERV-%' order by id
			""", Long.class).stream().map(id -> new Source(id, recipes)).toList();
	}

	private static long require(Map<String, Long> map, String key) {
		Long id = map.get(key);
		if (id == null) {
			throw new IllegalStateException("Demo tanim verisinde yok: " + key);
		}
		return id;
	}

	record SupplierField(long supplierId, Long fieldId) {
	}

	record Customer(long id, long addressId) {
	}

	record ProductSupplier(long id, String prefix) {
	}

	record Product(long id, long recipe, BigDecimal volume, Map<Long, Integer> components, long storage, int minimum,
		int maximum) {
	}

	record Source(long tankId, List<Recipe> recipes) {
	}

	/** Tarif: icerik oranlari (+-%2) ve asitlik araligi. */
	record Recipe(long id, Map<Long, BigDecimal> contents, BigDecimal minAcidity, BigDecimal maxAcidity,
		List<Product> products) {

		boolean matches(Map<Long, BigDecimal> mix, BigDecimal total, BigDecimal acidity) {
			for (Map.Entry<Long, BigDecimal> part : mix.entrySet()) {
				BigDecimal ratio = part.getValue().divide(total, 6, RoundingMode.HALF_UP);
				BigDecimal expected = contents.getOrDefault(part.getKey(), BigDecimal.ZERO);
				if (ratio.subtract(expected).abs().compareTo(TOLERANCE) > 0) {
					return false;
				}
			}
			for (Long type : contents.keySet()) {
				if (!mix.containsKey(type)) {
					return false;
				}
			}
			return (minAcidity == null || acidity.compareTo(minAcidity) >= 0)
				&& (maxAcidity == null || acidity.compareTo(maxAcidity) <= 0);
		}

	}

}

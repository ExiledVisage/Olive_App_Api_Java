package com.olivamore.api.analytics.analytics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Raporlarin SQL'i. Asamalar adlarindan degil gorevlerinden taninir: gelen zeytin (zeytin tartan ilk tartim), sikilan
 * zeytin (donusumun {@code input_quantity}'si), yag ve pirina (tartim), asitlik (yag analizi), tortu (on dinlendirme
 * tankinin bosaltilmasinda asamaya yazilan {@code residue_kg}; pirinaya eklenmez).
 */
@Component
class AnalyticsQueries {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String INCOMING = "incoming";
	private static final String PRESSED = "pressed";
	private static final String OIL = "oil";
	private static final String POMACE = "pomace";
	private static final String RESIDUE = "residue";
	private static final String ACIDITY = "acidity";

	/** Parti satiri: gelen, sikilan, yag, pirina, tortu, asitlik. */
	record BatchRow(long batchId, LocalDateTime receivedAt, Long supplierId, String supplierName, String supplierCode,
		Long fieldId, String fieldCode, Long productTypeId, String productTypeName, Double incoming, Double pressed,
		Double oil, Double pomace, Double residue, Double acidity) {
	}

	/** Iptal edilmemis siparisin kalemi. */
	record ItemRow(long orderId, Long customerId, String companyName, String customerName, LocalDate orderDate,
		Long finishedProductId, String productName, BigDecimal volumeLitre, long quantity) {
	}

	record CustomerNames(long id, String companyName, String name) {
	}

	private record Batch(long id, Long parentBatchId, BigDecimal quantityKg, LocalDateTime receivedAt, Long supplierId,
		String supplierName, String supplierCode, Long supplierFieldId, String fieldCode, Long productTypeId,
		String productTypeName) {
	}

	private record Process(long productionBatchId, long productionStageId, String operationData) {
	}

	private record Role(String name, int order) {
	}

	private record StageRow(long id, long productionLineId, int stageOrder) {
	}

	private record ExtensionRow(long id, long productionStageId, boolean isEnabled, String typeKey) {
	}

	private record ExtensionInput(long productionStageExtensionId, String productKey) {
	}

	/** Cikti satirini urun anahtarina cevirmek icin: tip id -> urun anahtari, birim, yogunluk; urun id -> anahtar. */
	private record OutputKeys(Map<Long, TypeKey> types, Map<Long, String> products) {
	}

	private record TypeKey(long id, String productKey, String unit, BigDecimal density) {
	}

	private final JdbcClient jdbc;

	AnalyticsQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean exists(String table, long id) {
		return jdbc.sql("select count(*) from " + table + " where id = :id").param("id", id).query(Long.class).single() > 0;
	}

	Optional<CustomerNames> customer(long id) {
		return jdbc.sql("select id, company_name, name from customers where id = :id").param("id", id)
			.query(CustomerNames.class).optional();
	}

	/**
	 * Aralikta gelmis, yagi bilinen ve sikilan miktari olan partiler. Gruplara bolunmus teslimat yerine gruplari
	 * sayilir; grubun gelen kilosu kendi kantari yoksa teslimatin kantarindan payi ({@link #deliveryShares}).
	 */
	List<BatchRow> batches(Range range, Long supplierId, Long productTypeId, Long fieldId) {
		List<Batch> batches = jdbc.sql("""
			select b.id, b.parent_batch_id, b.quantity_kg, b.received_at, b.supplier_id, b.supplier_field_id,
				(select s.name from suppliers s where s.id = b.supplier_id) as supplier_name,
				(select s.code from suppliers s where s.id = b.supplier_id) as supplier_code,
				(select f.code from supplier_fields f where f.id = b.supplier_field_id) as field_code,
				t.id as product_type_id, t.name as product_type_name
			from production_batches b
			left join lateral (
				select pt.id, pt.name from production_batch_input_production_product_types i
				join production_product_types pt on pt.id = i.production_product_type_id
				where i.production_batch_id = b.id
				order by i.sort_order, i.id limit 1
			) t on true
			where b.received_at between :from and :to
				and (cast(:supplier as bigint) is null or b.supplier_id = :supplier)
				and (cast(:type as bigint) is null or exists (
					select 1 from production_batch_input_production_product_types i
					where i.production_batch_id = b.id and i.production_product_type_id = :type))
				and (cast(:field as bigint) is null or b.supplier_field_id = :field)
				and not exists (select 1 from production_batches g where g.parent_batch_id = b.id)
			order by b.id
			""").param("from", range.from()).param("to", range.to()).param("supplier", supplierId)
			.param("type", productTypeId).param("field", fieldId).query(Batch.class).list();
		if (batches.isEmpty()) {
			return List.of();
		}
		Map<Long, Role> roles = stageRoles();
		Map<Long, List<Process>> processes = completedProcesses(batches.stream().map(Batch::id).toList(), roles.keySet());
		Map<Long, Double> deliveryShare = deliveryShares(batches, roles);
		OutputKeys keys = outputKeys();
		List<BatchRow> rows = new ArrayList<>();
		for (Batch batch : batches) {
			Map<String, Double> values = roleValues(processes.getOrDefault(batch.id(), List.of()), roles, keys);
			if (values.get(INCOMING) == null && deliveryShare.containsKey(batch.id())) {
				values.put(INCOMING, deliveryShare.get(batch.id()));
			}
			Double pressed = values.get(PRESSED) != null ? values.get(PRESSED) : values.get(INCOMING);
			rows.add(new BatchRow(batch.id(), batch.receivedAt(), batch.supplierId(), batch.supplierName(),
				batch.supplierCode(), batch.supplierFieldId(), batch.fieldCode(), batch.productTypeId(),
				batch.productTypeName(), values.get(INCOMING), pressed, values.get(OIL), values.get(POMACE),
				values.get(RESIDUE), values.get(ACIDITY)));
		}
		return rows.stream().filter(r -> r.oil() != null && r.pressed() != null && r.pressed() > 0).toList();
	}

	/**
	 * Kendi kantari olmayan grubun gelen kilosu: teslimatin kantar kilosundan, ayrilan kilosunun teslimatin butun
	 * gruplarina ayrilan kiloya orani kadar.
	 */
	private Map<Long, Double> deliveryShares(List<Batch> batches, Map<Long, Role> roles) {
		List<Batch> groups = batches.stream().filter(b -> b.parentBatchId() != null && b.quantityKg() != null).toList();
		if (groups.isEmpty()) {
			return Map.of();
		}
		List<Long> deliveries = groups.stream().map(Batch::parentBatchId).distinct().toList();
		Map<Long, List<Process>> deliveryProcesses = completedProcesses(deliveries, roles.keySet());
		Map<Long, BigDecimal> allocated = new LinkedHashMap<>();
		jdbc.sql("select parent_batch_id, sum(quantity_kg) as total from production_batches"
				+ " where parent_batch_id in (:ids) group by parent_batch_id")
			.param("ids", deliveries).query((rs, n) -> Map.entry(rs.getLong(1), rs.getBigDecimal(2))).list()
			.forEach(e -> allocated.put(e.getKey(), e.getValue()));
		Map<Long, Double> shares = new LinkedHashMap<>();
		for (Batch group : groups) {
			Double weighed = roleValues(deliveryProcesses.getOrDefault(group.parentBatchId(), List.of()), roles,
				new OutputKeys(Map.of(), Map.of())).get(INCOMING);
			BigDecimal total = allocated.get(group.parentBatchId());
			if (weighed != null && total != null && total.signum() > 0) {
				shares.put(group.id(), weighed * group.quantityKg().doubleValue() / total.doubleValue());
			}
		}
		return shares;
	}

	private Map<Long, List<Process>> completedProcesses(List<Long> batchIds, Set<Long> stageIds) {
		if (stageIds.isEmpty() || batchIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select production_batch_id, production_stage_id, operation_data::text as operation_data
			from batch_processes
			where production_batch_id in (:batches) and production_stage_id in (:stages) and status = 'completed'
			order by id
			""").param("batches", batchIds).param("stages", stageIds)
			.query(Process.class).list().stream()
			.collect(Collectors.groupingBy(Process::productionBatchId));
	}

	/** Asama sirasiyla: her gorev icin dolu ilk deger. Sikimin ciktilari yag ve pirinayi verir. */
	private static Map<String, Double> roleValues(List<Process> processes, Map<Long, Role> roles, OutputKeys keys) {
		Map<String, Double> values = new LinkedHashMap<>();
		processes.stream()
			.sorted(Comparator.comparingInt(p -> roles.get(p.productionStageId()).order()))
			.forEach(process -> {
				String role = roles.get(process.productionStageId()).name();
				JsonNode value = value(role, process.operationData());
				if (value != null && !value.isNull() && values.get(role) == null) {
					values.put(role, number(value));
				}
				if (PRESSED.equals(role) && process.operationData() != null) {
					outputs(JSON.readTree(process.operationData()).get("outputs"), keys)
						.forEach((output, kg) -> values.putIfAbsent(output, kg));
				}
			});
		return values;
	}

	/** Sikim ciktilarinin kilosu: zeytinyagi {@code oil}, pirina {@code pomace}; litre yogunlukla kiloya cevrilir. */
	private static Map<String, Double> outputs(JsonNode outputs, OutputKeys keys) {
		Map<String, Double> totals = new LinkedHashMap<>();
		if (outputs == null || !outputs.isArray()) {
			return totals;
		}
		for (JsonNode output : outputs.values()) {
			JsonNode quantity = output.get("quantity");
			if (quantity == null || quantity.isNull()) {
				continue;
			}
			TypeKey type = id(output.get("production_product_type_id")) == null ? null
				: keys.types().get(id(output.get("production_product_type_id")));
			String productKey = type != null ? type.productKey()
				: id(output.get("production_product_id")) != null ? keys.products().get(id(output.get("production_product_id")))
				: text(output.get("product_type_key"));
			String role = "olive_oil".equals(productKey) ? OIL : "pomace".equals(productKey) ? POMACE : null;
			if (role == null) {
				continue;
			}
			double kg = number(quantity);
			String unit = text(output.get("unit")) != null ? text(output.get("unit")) : type == null ? null : type.unit();
			if ("LITRE".equals(unit) && type != null && type.density() != null) {
				kg *= type.density().doubleValue();
			}
			totals.merge(role, kg, Double::sum);
		}
		return totals;
	}

	private static Long id(JsonNode value) {
		return value == null || value.isNull() ? null : (long) number(value);
	}

	private static String text(JsonNode value) {
		return value != null && value.isString() ? value.asString() : null;
	}

	private OutputKeys outputKeys() {
		Map<Long, TypeKey> types = jdbc.sql("""
			select t.id, p.key as product_key, t.unit,
				coalesce(nullif(t.density_kg_per_litre, 0), p.default_density_kg_per_litre) as density
			from production_product_types t join production_products p on p.id = t.production_product_id
			""").query(TypeKey.class).list().stream().collect(Collectors.toMap(TypeKey::id, t -> t));
		Map<Long, String> products = new LinkedHashMap<>();
		jdbc.sql("select id, key from production_products where key is not null")
			.query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
			.forEach(e -> products.put(e.getKey(), e.getValue()));
		return new OutputKeys(types, products);
	}

	private static JsonNode value(String role, String operationData) {
		if (operationData == null) {
			return null;
		}
		JsonNode data = JSON.readTree(operationData);
		return switch (role) {
			case PRESSED -> data.get("input_quantity");
			case RESIDUE -> data.get("residue_kg");
			case ACIDITY -> acidity(data.get("attributes"));
			default -> data.get("quantity");
		};
	}

	/** {@code attribute_key} degeri acidity olan ilk kaydin {@code value}'su. */
	private static JsonNode acidity(JsonNode attributes) {
		if (attributes == null) {
			return null;
		}
		for (JsonNode attribute : attributes.values()) {
			JsonNode key = attribute.get("attribute_key");
			if (key != null && key.isString() && ACIDITY.equals(key.asString())) {
				return attribute.get("value");
			}
		}
		return null;
	}

	/** Metin olarak saklanmis sayilar da okunur; sayi degilse sifir. */
	private static double number(JsonNode value) {
		if (value.isNumber()) {
			return value.doubleValue();
		}
		if (value.isString()) {
			try {
				return Double.parseDouble(value.asString().strip());
			}
			catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	/** Asama id -> gorev ve sira. Gelen zeytin yalnizca hattin ilk zeytin tartimidir. */
	private Map<Long, Role> stageRoles() {
		List<StageRow> stages = jdbc.sql("""
			select id, production_line_id, stage_order from production_stages
			order by production_line_id asc, stage_order asc
			""").query(StageRow.class).list();
		Map<Long, List<ExtensionRow>> extensions = jdbc.sql("""
			select e.id, e.production_stage_id, e.is_enabled, t.key as type_key
			from production_stage_extensions e
			left join stage_extension_types t on t.id = e.stage_extension_type_id
			order by e.sort_order, e.id
			""").query(ExtensionRow.class).list().stream()
			.collect(Collectors.groupingBy(ExtensionRow::productionStageId));
		Map<Long, List<String>> inputs = jdbc.sql("""
			select i.production_stage_extension_id, p.key as product_key
			from production_stage_extension_input_production_products i
			left join production_products p on p.id = i.production_product_id
			order by i.sort_order, i.id
			""").query(ExtensionInput.class).list().stream()
			.filter(i -> i.productKey() != null)
			.collect(Collectors.groupingBy(ExtensionInput::productionStageExtensionId,
				Collectors.mapping(ExtensionInput::productKey, Collectors.toList())));
		Map<Long, Role> roles = new LinkedHashMap<>();
		Set<Long> linesWithIncoming = new HashSet<>();
		for (StageRow stage : stages) {
			for (ExtensionRow extension : extensions.getOrDefault(stage.id(), List.of())) {
				if (!extension.isEnabled()) {
					continue;
				}
				String role = role(extension.typeKey(), inputs.getOrDefault(extension.id(), List.of()),
					linesWithIncoming.contains(stage.productionLineId()));
				if (role == null) {
					continue;
				}
				if (INCOMING.equals(role)) {
					linesWithIncoming.add(stage.productionLineId());
				}
				roles.put(stage.id(), new Role(role, stage.stageOrder()));
			}
		}
		return roles;
	}

	private static String role(String type, List<String> productKeys, boolean lineHasIncoming) {
		if ("weighing".equals(type) && productKeys.contains("olive") && !lineHasIncoming) {
			return INCOMING;
		}
		if ("material_transformation".equals(type) && productKeys.contains("olive")) {
			return PRESSED;
		}
		if ("weighing".equals(type) && productKeys.contains("olive_oil")) {
			return OIL;
		}
		if ("weighing".equals(type) && productKeys.contains("pomace")) {
			return POMACE;
		}
		if ("pre_settling".equals(type)) {
			return RESIDUE;
		}
		if ("analysis".equals(type) && productKeys.contains("olive_oil")) {
			return ACIDITY;
		}
		return null;
	}

	/** Araliktaki iptal edilmemis siparislerin kalemleri. */
	List<ItemRow> orderItems(LocalDate from, LocalDate to, Long customerId, Long finishedProductId) {
		return jdbc.sql("""
			select i.order_id, o.customer_id, c.company_name, c.name as customer_name, o.order_date,
				i.finished_product_id, p.name as product_name, p.volume_litre, i.quantity
			from order_items i
			join orders o on o.id = i.order_id
			left join customers c on c.id = o.customer_id
			left join finished_products p on p.id = i.finished_product_id
			where o.status <> 'cancelled' and o.order_date between :from and :to
				and (cast(:customer as bigint) is null or o.customer_id = :customer)
				and (cast(:product as bigint) is null or i.finished_product_id = :product)
			order by i.id
			""").param("from", from).param("to", to).param("customer", customerId)
			.param("product", finishedProductId).query(ItemRow.class).list();
	}

}

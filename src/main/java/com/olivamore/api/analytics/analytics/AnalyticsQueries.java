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
 * zeytin (donusumun {@code input_quantity}'si), yag ve pirina (tartim), asitlik (yag analizi).
 */
@Component
class AnalyticsQueries {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String INCOMING = "incoming";
	private static final String PRESSED = "pressed";
	private static final String OIL = "oil";
	private static final String POMACE = "pomace";
	private static final String ACIDITY = "acidity";

	/** Parti satiri: gelen, sikilan, yag, pirina, asitlik. */
	record BatchRow(long batchId, LocalDateTime receivedAt, Long supplierId, String supplierName, String supplierCode,
		Long fieldId, String fieldCode, Long productTypeId, String productTypeName, Double incoming, Double pressed,
		Double oil, Double pomace, Double acidity) {
	}

	/** Iptal edilmemis siparisin kalemi. */
	record ItemRow(long orderId, Long customerId, String companyName, String customerName, LocalDate orderDate,
		Long finishedProductId, String productName, BigDecimal volumeLitre, long quantity) {
	}

	record CustomerNames(long id, String companyName, String name) {
	}

	private record Batch(long id, LocalDateTime receivedAt, Long supplierId, String supplierName, String supplierCode,
		Long supplierFieldId, String fieldCode, Long productTypeId, String productTypeName) {
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

	/** Aralikta gelmis, yagi tartilmis ve sikilan miktari olan partiler. */
	List<BatchRow> batches(Range range, Long supplierId, Long productTypeId, Long fieldId) {
		List<Batch> batches = jdbc.sql("""
			select b.id, b.received_at, b.supplier_id, b.supplier_field_id,
				(select s.name from suppliers s where s.id = b.supplier_id) as supplier_name,
				(select s.code from suppliers s where s.id = b.supplier_id) as supplier_code,
				(select f.code from supplier_fields f where f.id = b.supplier_field_id) as field_code,
				t.id as product_type_id, t.name as product_type_name
			from production_batches b
			left join lateral (
				select pt.id, pt.name from production_batch_input_production_product_types i
				join production_product_types pt on pt.id = i.production_product_type_id
				where i.production_batch_id = b.id
				order by i.sort_order limit 1
			) t on true
			where b.received_at between :from and :to
				and (cast(:supplier as bigint) is null or b.supplier_id = :supplier)
				and (cast(:type as bigint) is null or exists (
					select 1 from production_batch_input_production_product_types i
					where i.production_batch_id = b.id and i.production_product_type_id = :type))
				and (cast(:field as bigint) is null or b.supplier_field_id = :field)
			""").param("from", range.from()).param("to", range.to()).param("supplier", supplierId)
			.param("type", productTypeId).param("field", fieldId).query(Batch.class).list();
		if (batches.isEmpty()) {
			return List.of();
		}
		Map<Long, Role> roles = stageRoles();
		Map<Long, List<Process>> processes = completedProcesses(batches, roles.keySet());
		List<BatchRow> rows = new ArrayList<>();
		for (Batch batch : batches) {
			Map<String, Double> values = roleValues(processes.getOrDefault(batch.id(), List.of()), roles);
			Double pressed = values.get(PRESSED) != null ? values.get(PRESSED) : values.get(INCOMING);
			rows.add(new BatchRow(batch.id(), batch.receivedAt(), batch.supplierId(), batch.supplierName(),
				batch.supplierCode(), batch.supplierFieldId(), batch.fieldCode(), batch.productTypeId(),
				batch.productTypeName(), values.get(INCOMING), pressed, values.get(OIL), values.get(POMACE),
				values.get(ACIDITY)));
		}
		return rows.stream().filter(r -> r.oil() != null && r.pressed() != null && r.pressed() > 0).toList();
	}

	private Map<Long, List<Process>> completedProcesses(List<Batch> batches, Set<Long> stageIds) {
		if (stageIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
			select production_batch_id, production_stage_id, operation_data::text as operation_data
			from batch_processes
			where production_batch_id in (:batches) and production_stage_id in (:stages) and status = 'completed'
			""").param("batches", batches.stream().map(Batch::id).toList()).param("stages", stageIds)
			.query(Process.class).list().stream()
			.collect(Collectors.groupingBy(Process::productionBatchId));
	}

	/** Asama sirasiyla: her gorev icin dolu ilk deger. */
	private static Map<String, Double> roleValues(List<Process> processes, Map<Long, Role> roles) {
		Map<String, Double> values = new LinkedHashMap<>();
		processes.stream()
			.sorted(Comparator.comparingInt(p -> roles.get(p.productionStageId()).order()))
			.forEach(process -> {
				String role = roles.get(process.productionStageId()).name();
				JsonNode value = value(role, process.operationData());
				if (value != null && !value.isNull() && values.get(role) == null) {
					values.put(role, number(value));
				}
			});
		return values;
	}

	private static JsonNode value(String role, String operationData) {
		if (operationData == null) {
			return null;
		}
		JsonNode data = JSON.readTree(operationData);
		return switch (role) {
			case PRESSED -> data.get("input_quantity");
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
			order by e.sort_order
			""").query(ExtensionRow.class).list().stream()
			.collect(Collectors.groupingBy(ExtensionRow::productionStageId));
		Map<Long, List<String>> inputs = jdbc.sql("""
			select i.production_stage_extension_id, p.key as product_key
			from production_stage_extension_input_production_products i
			left join production_products p on p.id = i.production_product_id
			order by i.sort_order
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

package com.olivamore.api.supplier;

import com.olivamore.api.supplier.SupplierDeliverySource.BatchDelivery;
import com.olivamore.api.supplier.SupplierDeliverySource.WeighIn;
import com.olivamore.api.supplier.SupplierDeliverySource.WeighInValue;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.Delivery;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.PurchaseSummary;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.Summary;
import com.olivamore.api.supplier.SupplierDeliveriesResponse.WeighInField;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Teslimatlar {@link SupplierDeliverySource} uzerinden uretim modulunden gelir. */
@Service
class SupplierDeliveryService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final SupplierDeliverySource source;
	private final SupplierDirectory directory;
	private final SupplierService suppliers;

	SupplierDeliveryService(SupplierDeliverySource source, SupplierDirectory directory, SupplierService suppliers) {
		this.source = source;
		this.directory = directory;
		this.suppliers = suppliers;
	}

	/** {@code dateFrom}/{@code dateTo}: varis gunu araligi (ISO tarih), verilmezse butun teslimatlar. */
	@Transactional(readOnly = true)
	SupplierDeliveriesResponse forSupplier(long supplierId, String dateFrom, String dateTo) {
		suppliers.ensureExists(supplierId);
		LocalDate from = date("date_from", dateFrom);
		LocalDate to = date("date_to", dateTo);
		boolean prices = source.pricesVisible();
		List<BatchDelivery> batches = source.batches(supplierId, from, to);
		if (batches.isEmpty()) {
			return new SupplierDeliveriesResponse(List.of(), new Summary(0, BigDecimal.ZERO, null,
				prices ? purchaseSummary(List.of()) : null));
		}
		List<Long> ids = batches.stream().map(BatchDelivery::id).toList();
		Map<Long, List<String>> names = source.inputProductNames(ids);
		Map<Long, WeighIn> weighIns = source.firstWeighIns(ids);
		Map<Long, List<WeighInField>> fields = fieldValues(weighIns.values().stream().map(WeighIn::id).toList());
		Map<Long, SupplierDirectory.Field> supplierFields = directory.fields(
			batches.stream().map(BatchDelivery::supplierFieldId).toList());

		List<Delivery> deliveries = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (BatchDelivery batch : batches) {
			WeighIn weighIn = weighIns.get(batch.id());
			SupplierDirectory.Field field = batch.supplierFieldId() == null ? null
				: supplierFields.get(batch.supplierFieldId());
			JsonNode quantity = weighIn == null || weighIn.quantityJson() == null
				? null : JSON.readTree(weighIn.quantityJson());
			total = total.add(number(quantity));
			deliveries.add(new Delivery(batch.id(), batch.batchCode(), batch.status(), batch.receivedAt(),
				field == null ? null : field.code(), names.getOrDefault(batch.id(), List.of()), batch.quantityKg(),
				batch.truckPlate(), batch.weighingSlipNo(), quantity,
				weighIn == null ? null : weighIn.completedAt(),
				weighIn == null ? List.of() : fields.getOrDefault(weighIn.id(), List.of()), batch.purchase()));
		}
		BigDecimal rounded = new BigDecimal(
			total.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
		return new SupplierDeliveriesResponse(deliveries, new Summary(deliveries.size(), rounded,
			deliveries.getFirst().receivedAt(), prices ? purchaseSummary(batches) : null));
	}

	/** Fiyati girilmis teslimatlarin toplamlari; nakliye yalnizca fiyatli teslimatlarda sayilir. */
	private static PurchaseSummary purchaseSummary(List<BatchDelivery> batches) {
		int count = 0;
		BigDecimal kg = BigDecimal.ZERO;
		BigDecimal totalEx = BigDecimal.ZERO;
		BigDecimal totalWith = BigDecimal.ZERO;
		for (BatchDelivery batch : batches) {
			SupplierDeliverySource.Purchase purchase = batch.purchase();
			if (purchase == null || purchase.pricePerKg() == null || batch.quantityKg() == null) {
				continue;
			}
			count++;
			kg = kg.add(batch.quantityKg());
			totalEx = totalEx.add(purchase.totalExVat());
			totalWith = totalWith.add(purchase.totalWithVat());
		}
		return new PurchaseSummary(count, kg.setScale(2, RoundingMode.HALF_UP), totalEx, totalWith,
			kg.signum() > 0 ? totalEx.divide(kg, 4, RoundingMode.HALF_UP) : null,
			kg.signum() > 0 ? totalWith.divide(kg, 4, RoundingMode.HALF_UP) : null);
	}

	private static LocalDate date(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException e) {
			throw ValidationFailedException.of(field, MessageKeys.DATE);
		}
	}

	private Map<Long, List<WeighInField>> fieldValues(List<Long> processIds) {
		return source.fieldValues(processIds).stream()
			.filter(row -> value(row) != null)
			.collect(Collectors.groupingBy(WeighInValue::processId, Collectors.mapping(
				row -> new WeighInField(row.fieldKey(), row.label(), value(row)), Collectors.toList())));
	}

	private static Object value(WeighInValue row) {
		if (row.valueText() != null) {
			return row.valueText();
		}
		return row.valueDecimal() != null ? row.valueDecimal().toPlainString() : row.valueInteger();
	}

	private static BigDecimal number(JsonNode node) {
		if (node == null || node.isNull()) {
			return BigDecimal.ZERO;
		}
		if (node.isNumber()) {
			return node.decimalValue();
		}
		try {
			return new BigDecimal(node.asString().strip());
		}
		catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

}

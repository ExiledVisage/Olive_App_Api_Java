package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.inventory.material.LocationType;
import com.olivamore.api.inventory.material.Material;
import com.olivamore.api.inventory.material.MaterialAttributeValue;
import com.olivamore.api.inventory.material.MaterialAttributeValueRepository;
import com.olivamore.api.inventory.material.MaterialRepository;
import com.olivamore.api.inventory.material.MaterialStatus;
import com.olivamore.api.inventory.material.Quantities;
import com.olivamore.api.inventory.storagemovement.TankQueries.RuleDefinition;
import com.olivamore.api.inventory.storageunit.StorageUnit;
import com.olivamore.api.inventory.storageunit.StorageUnitKind;
import com.olivamore.api.inventory.storageunit.StorageUnitRepository;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Son tank analizi: olculen sayisal deger tank materyalinin hesaplanan degerinin yerine gecer, sonraki girisler
 * ortalamayi bu degerden surdurur. Her olcum hesaplanan deger, olculen deger ve farkla gecmise yazilir.
 */
@Service
class TankMeasurementService {

	private static final Set<String> NUMERIC = Set.of("decimal", "integer");

	private final StorageUnitRepository units;
	private final MaterialRepository materials;
	private final MaterialAttributeValueRepository values;
	private final TankMeasurementRepository measurements;
	private final TankQueries queries;
	private final UserDirectory users;

	@PersistenceContext
	private EntityManager entityManager;

	TankMeasurementService(StorageUnitRepository units, MaterialRepository materials,
		MaterialAttributeValueRepository values, TankMeasurementRepository measurements, TankQueries queries,
		UserDirectory users) {
		this.units = units;
		this.materials = materials;
		this.values = values;
		this.measurements = measurements;
		this.queries = queries;
		this.users = users;
	}

	@Transactional
	List<TankMeasurementResponse> measure(long unitId, TankMeasurementRequest request, long userId) {
		StorageUnit unit = units.findById(unitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", unitId));
		List<Long> definitionIds = request.attributes().stream()
			.map(TankMeasurementRequest.Attribute::productionProductAttributeDefinitionId).toList();
		ValidationErrors errors = new ValidationErrors();
		if (!queries.materialExists(request.materialId())) {
			errors.add("material_id", MessageKeys.EXISTS);
		}
		errors.distinct("attributes", "production_product_attribute_definition_id", definitionIds);
		errors.exists("attributes", "production_product_attribute_definition_id", definitionIds,
			queries.existingDefinitions(definitionIds));
		errors.throwIfAny();

		if (unit.getKind() != StorageUnitKind.FINAL) {
			throw new BusinessRuleException("Tank analizi yalnızca son tanklarda yapılır.");
		}
		Material material = material(unit, request.materialId());
		Long productId = queries.productType(material.getProductionProductTypeId()).orElseThrow()
			.productionProductId();
		Set<Long> ofProduct = queries.definitionsOfProduct(productId);
		Map<Long, RuleDefinition> definitions = queries.ruleDefinitions(definitionIds);
		for (Long id : definitionIds) {
			if (!ofProduct.contains(id)) {
				throw ValidationFailedException.of("attributes", "Geçersiz attribute definition gönderildi.");
			}
			if (!NUMERIC.contains(definitions.get(id).dataType())) {
				throw new BusinessRuleException("Tank analizi yalnızca sayısal özellikler için yapılır.");
			}
		}

		LocalDateTime now = Timestamps.now();
		List<TankMeasurement> saved = new ArrayList<>();
		for (TankMeasurementRequest.Attribute attribute : request.attributes()) {
			long definitionId = attribute.productionProductAttributeDefinitionId();
			boolean integer = "integer".equals(definitions.get(definitionId).dataType());
			BigDecimal measured = attribute.value();
			MaterialAttributeValue current = values.findByMaterialIdAndDefinitionId(material.getId(), definitionId)
				.orElse(null);
			BigDecimal calculated = current == null ? null : number(current);
			Long integerValue = integer ? measured.setScale(0, RoundingMode.HALF_UP).longValueExact() : null;
			BigDecimal decimalValue = integer ? null : measured;
			if (current == null) {
				values.save(new MaterialAttributeValue(material.getId(), definitionId, null, integerValue, decimalValue,
					null, null));
			}
			else {
				current.update(null, integerValue, decimalValue, null, null);
			}
			saved.add(measurements.save(new TankMeasurement(unit.getId(), material.getId(), definitionId, calculated,
				measured, userId, request.note(), now)));
		}
		entityManager.flush();
		return responses(saved);
	}

	@Transactional(readOnly = true)
	List<TankMeasurementResponse> list(long unitId, Long materialId) {
		if (!units.existsById(unitId)) {
			throw new ModelNotFoundException("StorageUnit", unitId);
		}
		return responses(materialId == null ? measurements.findByStorageUnitIdOrderByMeasuredAtDescIdDesc(unitId)
			: measurements.findByStorageUnitIdAndMaterialIdOrderByMeasuredAtDescIdDesc(unitId, materialId));
	}

	/** Tankta duran aktif materyal. */
	private Material material(StorageUnit unit, long id) {
		Material material = materials.findById(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
		entityManager.refresh(material);
		if (material.getCurrentLocationType() != LocationType.STORAGE_UNIT
			|| !Objects.equals(material.getCurrentLocationId(), unit.getId())) {
			throw new BusinessRuleException("Seçilen material bu tankın içinde bulunmuyor.");
		}
		if (material.getStatus() != MaterialStatus.ACTIVE) {
			throw new BusinessRuleException("Sadece aktif material üzerinde işlem yapılabilir.");
		}
		return material;
	}

	private List<TankMeasurementResponse> responses(List<TankMeasurement> rows) {
		Map<Long, RuleDefinition> definitions = queries.ruleDefinitions(
			rows.stream().map(TankMeasurement::getDefinitionId).toList());
		Map<Long, UserDirectory.User> byId = users.users(
			rows.stream().map(TankMeasurement::getMeasuredByUserId).filter(Objects::nonNull).toList());
		return rows.stream().map(m -> {
			RuleDefinition definition = definitions.get(m.getDefinitionId());
			String label = definition == null ? null
				: definition.label() != null ? definition.label() : definition.attributeKey();
			UserDirectory.User user = m.getMeasuredByUserId() == null ? null : byId.get(m.getMeasuredByUserId());
			return new TankMeasurementResponse(m.getId(), m.getStorageUnitId(), m.getMaterialId(), m.getDefinitionId(),
				label, Quantities.number(m.getCalculatedValue()), Quantities.number(m.getMeasuredValue()),
				Quantities.number(m.getDifference()),
				user == null ? null : new TankMeasurementResponse.MeasuredBy(user.id(), user.name()), m.getNote(),
				m.getMeasuredAt());
		}).toList();
	}

	/** Sayisal deger: decimal, yoksa integer. */
	private static BigDecimal number(MaterialAttributeValue value) {
		if (value.getValueDecimal() != null) {
			return value.getValueDecimal();
		}
		return value.getValueInteger() == null ? null : BigDecimal.valueOf(value.getValueInteger());
	}

}

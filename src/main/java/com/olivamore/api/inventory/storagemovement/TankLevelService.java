package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.inventory.MixShares;
import com.olivamore.api.inventory.material.Material;
import com.olivamore.api.inventory.material.MaterialRepository;
import com.olivamore.api.inventory.storageunit.StorageUnit;
import com.olivamore.api.inventory.storageunit.StorageUnitRepository;
import com.olivamore.api.inventory.storageunit.TankGeometry;
import com.olivamore.api.inventory.storagemovement.TankQueries.ProductType;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tank seviyesi (docs/GREENOPS_FEATURES.md 4): tank geometrisi ve cetvel okumasi. Okuma stoku degistirmez; hacim
 * geometriden, kg tanktaki karisimin yogunluguyla (toplam kg / toplam litre; tank bossa kg yok), defter miktari tankta
 * su an duran materyallerin toplami (tankin biriminde). Fark = hesaplanan - defter; duzeltme gerekirse kullanici
 * sayim duzeltmesini ayrica yapar.
 */
@Service
class TankLevelService {

	private final StorageUnitRepository units;
	private final TankLevelReadingRepository readings;
	private final TankQueries queries;
	private final MaterialRepository materials;
	private final UserDirectory users;

	TankLevelService(StorageUnitRepository units, TankLevelReadingRepository readings, TankQueries queries,
		MaterialRepository materials, UserDirectory users) {
		this.units = units;
		this.readings = readings;
		this.queries = queries;
		this.materials = materials;
		this.users = users;
	}

	@Transactional(readOnly = true)
	TankLevelResponse.Geometry geometry(long unitId) {
		return geometryOf(find(unitId));
	}

	/** Istek bilginin tamami. */
	@Transactional
	TankLevelResponse.Geometry updateGeometry(long unitId, TankGeometryRequest request) {
		StorageUnit unit = find(unitId);
		unit.changeGeometry(new TankGeometry(request.innerDiameterMm(), request.cylinderHeightMm(),
			request.bottomConeDeg(), request.topConeDeg(), request.rulerOffsetMm(), request.deadVolumeLitre()));
		return geometryOf(unit);
	}

	@Transactional
	void deleteGeometry(long unitId) {
		find(unitId).changeGeometry(null);
	}

	@Transactional
	TankLevelResponse.Reading read(long unitId, TankLevelReadingRequest request, long userId) {
		StorageUnit unit = find(unitId);
		TankGeometry geometry = unit.geometry().orElseThrow(() -> new BusinessRuleException("'" + unit.getName()
			+ "' tankının geometrisi girilmemiş; önce tank ayarlarından ölçüleri girin."));
		BigDecimal litres = geometry.litresAt(request.measuredMm());
		List<MixShares.Part> parts = contents(unitId);
		BigDecimal density = density(parts);
		BigDecimal kg = density == null ? null : litres.multiply(density).setScale(2, RoundingMode.HALF_UP);
		String capacityUnit = unit.getCapacityUnit().value();
		BigDecimal book = parts.isEmpty() ? BigDecimal.ZERO
			: MixShares.total(parts, capacityUnit).setScale(2, RoundingMode.HALF_UP);
		BigDecimal calculated = "LITRE".equals(capacityUnit) ? litres : kg;
		BigDecimal difference = calculated == null ? null : calculated.subtract(book);
		TankLevelReading reading = readings.save(new TankLevelReading(unitId, request.measuredMm(), litres, kg,
			density, book, unit.getCapacityUnit(), difference, userId, request.note(), Timestamps.now()));
		return responses(List.of(reading), geometry).getFirst();
	}

	/** En yeni once. */
	@Transactional(readOnly = true)
	List<TankLevelResponse.Reading> list(long unitId) {
		StorageUnit unit = find(unitId);
		return responses(readings.findByStorageUnitIdOrderByMeasuredAtDescIdDesc(unitId), unit.geometry().orElse(null));
	}

	private List<MixShares.Part> contents(long unitId) {
		List<Material> rows = materials.findAllById(queries.activeMaterialIds(unitId));
		Map<Long, ProductType> types = queries.productTypes(
			rows.stream().map(Material::getProductionProductTypeId).distinct().toList());
		return rows.stream().map(m -> {
			ProductType type = types.get(m.getProductionProductTypeId());
			return new MixShares.Part(m.getQuantity(), m.getUnit().value(), type == null ? null : type.density(),
				type == null ? null : type.name());
		}).toList();
	}

	/** Karisimin yogunlugu; tank bossa ya da bir tipin yogunlugu yoksa null. */
	private static BigDecimal density(List<MixShares.Part> parts) {
		if (parts.isEmpty()) {
			return null;
		}
		try {
			BigDecimal litres = MixShares.total(parts, "LITRE");
			return litres.signum() <= 0 ? null : MixShares.total(parts, "KG").divide(litres, MathContext.DECIMAL64)
				.setScale(4, RoundingMode.HALF_UP);
		}
		catch (BusinessRuleException e) {
			return null;
		}
	}

	private List<TankLevelResponse.Reading> responses(List<TankLevelReading> rows, TankGeometry geometry) {
		Map<Long, UserDirectory.User> byId = users.users(
			rows.stream().map(TankLevelReading::getMeasuredByUserId).filter(Objects::nonNull).toList());
		return rows.stream().map(r -> {
			UserDirectory.User user = r.getMeasuredByUserId() == null ? null : byId.get(r.getMeasuredByUserId());
			BigDecimal percent = r.getDifference() == null || r.getBookQuantity().signum() <= 0 ? null
				: r.getDifference().multiply(BigDecimal.valueOf(100)).divide(r.getBookQuantity(), 2, RoundingMode.HALF_UP);
			return new TankLevelResponse.Reading(r.getId(), r.getStorageUnitId(), r.getMeasuredMm(),
				r.getCalculatedLitre(), r.getCalculatedKg(), r.getDensityKgPerLitre(), r.getBookQuantity(),
				r.getUnit().value(), r.getDifference(), percent,
				user == null ? null : new TankLevelResponse.MeasuredBy(user.id(), user.name()), r.getNote(),
				r.getMeasuredAt());
		}).toList();
	}

	private static TankLevelResponse.Geometry geometryOf(StorageUnit unit) {
		return unit.geometry().map(g -> new TankLevelResponse.Geometry(g.innerDiameterMm(), g.cylinderHeightMm(),
			g.bottomConeDeg(), g.topConeDeg(), g.rulerOffsetMm(), g.deadVolumeLitre(), g.maxReadingMm(),
			g.fullLitres())).orElse(null);
	}

	private StorageUnit find(long unitId) {
		return units.findById(unitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", unitId));
	}

}

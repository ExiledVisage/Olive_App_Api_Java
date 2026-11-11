package com.olivamore.api.inventory.storagemovement;

import com.olivamore.api.inventory.material.MaterialAttributeValue;
import com.olivamore.api.inventory.material.MaterialAttributeValueRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Son tanka giriste tank materyalinin attribute degerleri. Tank materyali yeni aciliyorsa gelenin degerleri aynen
 * kopyalanir. Doluysa sayisal (decimal, integer) degerler miktara gore agirlikli ortalama olur: tank materyali ve
 * gelen ayni tipte (ayni yogunluk) oldugundan tipin birimindeki miktar kutleyle ayni agirligi verir. Diger degerler
 * ayniysa kalir, farkliysa bosalir; iki taraftan birinde deger yoksa karisimin degeri bilinmez ve bosalir.
 */
@Component
class TankMixAttributes {

	private static final Set<String> NUMERIC = Set.of("decimal", "integer");

	private final MaterialAttributeValueRepository values;
	private final TankQueries queries;

	TankMixAttributes(MaterialAttributeValueRepository values, TankQueries queries) {
		this.values = values;
		this.queries = queries;
	}

	/**
	 * @param tankQuantity tank materyalinin giristen onceki miktari (0 ise yeni)
	 * @param incomingQuantity gelen miktar, tank materyalinin biriminde
	 */
	void merge(long tankMixId, BigDecimal tankQuantity, long incomingId, BigDecimal incomingQuantity) {
		Map<Long, MaterialAttributeValue> incoming = byDefinition(incomingId);
		if (tankQuantity.signum() <= 0) {
			values.deleteAll(values.findByMaterialIdOrderByIdAsc(tankMixId));
			values.flush();
			incoming.values().forEach(v -> values.save(new MaterialAttributeValue(tankMixId, v.getDefinitionId(),
				v.getValueBoolean(), v.getValueInteger(), v.getValueDecimal(), v.getValueText(), v.getValueJson())));
			return;
		}
		Map<Long, MaterialAttributeValue> tank = byDefinition(tankMixId);
		Set<Long> definitions = new HashSet<>(tank.keySet());
		Map<Long, String> dataTypes = queries.dataTypes(definitions);
		for (MaterialAttributeValue current : tank.values()) {
			MaterialAttributeValue other = incoming.get(current.getDefinitionId());
			String dataType = dataTypes.get(current.getDefinitionId());
			if (other == null) {
				values.delete(current);
			}
			else if (NUMERIC.contains(dataType)) {
				average(current, other, tankQuantity, incomingQuantity, "integer".equals(dataType));
			}
			else if (!sameValue(current, other)) {
				values.delete(current);
			}
		}
	}

	private void average(MaterialAttributeValue current, MaterialAttributeValue other, BigDecimal tankQuantity,
		BigDecimal incomingQuantity, boolean integer) {
		BigDecimal a = number(current);
		BigDecimal b = number(other);
		if (a == null || b == null) {
			values.delete(current);
			return;
		}
		BigDecimal mean = a.multiply(tankQuantity).add(b.multiply(incomingQuantity))
			.divide(tankQuantity.add(incomingQuantity), MathContext.DECIMAL128);
		if (integer) {
			current.update(null, mean.setScale(0, RoundingMode.HALF_UP).longValueExact(), null, null, null);
		}
		else {
			current.update(null, null, mean, null, null);
		}
	}

	/** Sayisal deger: decimal, yoksa integer. */
	private static BigDecimal number(MaterialAttributeValue value) {
		if (value.getValueDecimal() != null) {
			return value.getValueDecimal();
		}
		return value.getValueInteger() == null ? null : BigDecimal.valueOf(value.getValueInteger());
	}

	private static boolean sameValue(MaterialAttributeValue a, MaterialAttributeValue b) {
		return Objects.equals(a.getValueBoolean(), b.getValueBoolean())
			&& Objects.equals(a.getValueInteger(), b.getValueInteger())
			&& (a.getValueDecimal() == null ? b.getValueDecimal() == null
				: b.getValueDecimal() != null && a.getValueDecimal().compareTo(b.getValueDecimal()) == 0)
			&& Objects.equals(a.getValueText(), b.getValueText())
			&& Objects.equals(a.getValueJson(), b.getValueJson());
	}

	private Map<Long, MaterialAttributeValue> byDefinition(long materialId) {
		List<MaterialAttributeValue> rows = values.findByMaterialIdOrderByIdAsc(materialId);
		return rows.stream().collect(Collectors.toMap(MaterialAttributeValue::getDefinitionId, Function.identity(),
			(first, second) -> first, LinkedHashMap::new));
	}

}

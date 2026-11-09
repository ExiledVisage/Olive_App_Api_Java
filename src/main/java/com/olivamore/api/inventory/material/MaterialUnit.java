package com.olivamore.api.inventory.material;

import com.olivamore.api.shared.persistence.ValueEnum;
import com.olivamore.api.shared.persistence.ValueEnumConverter;
import jakarta.persistence.Converter;

/** Miktar birimi (materyal, tank kapasitesi, bakiye, hareket). */
public enum MaterialUnit implements ValueEnum {

	KG("KG"),
	LITRE("LITRE");

	private final String value;

	MaterialUnit(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

	/** Bilinmeyen metin {@code null}. */
	public static MaterialUnit of(String text) {
		for (MaterialUnit unit : values()) {
			if (unit.value.equals(text)) {
				return unit;
			}
		}
		return null;
	}

	@Converter
	public static class UnitConverter extends ValueEnumConverter<MaterialUnit> {

		public UnitConverter() {
			super(MaterialUnit.class);
		}

	}

}

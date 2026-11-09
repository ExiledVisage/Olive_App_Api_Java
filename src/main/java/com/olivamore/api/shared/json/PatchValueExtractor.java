package com.olivamore.api.shared.json;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/** {@code Patch<@Size(max = 50) String>}: kisit, sarili degere uygulanir. */
public class PatchValueExtractor implements ValueExtractor<Patch<@ExtractedValue ?>> {

	@Override
	public void extractValues(Patch<?> patch, ValueReceiver receiver) {
		receiver.value(null, patch.value());
	}

}

package com.olivamore.api.shared.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.function.Consumer;

/**
 * PATCH isteginde temizlenebilen alan: alan hic gelmediyse request'teki deger {@code null},
 * {@code null} geldiyse {@code new Patch<>(null)}.
 * <pre>record UpdateRequest(Patch&lt;String&gt; description) {}</pre>
 */
@JsonDeserialize(using = Patch.Deserializer.class)
public record Patch<T>(T value) {

	/** Alan istekte geldiyse degerini (null olabilir) verir. */
	public static <T> void ifPresent(Patch<T> patch, Consumer<T> apply) {
		if (patch != null) {
			apply.accept(patch.value());
		}
	}

	public static final class Deserializer extends ValueDeserializer<Patch<?>> {

		private final ValueDeserializer<Object> value;

		public Deserializer() {
			this(null);
		}

		private Deserializer(ValueDeserializer<Object> value) {
			this.value = value;
		}

		@Override
		public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
			JavaType type = property != null ? property.getType() : ctxt.getContextualType();
			return new Deserializer(ctxt.findContextualValueDeserializer(type.containedType(0), property));
		}

		@Override
		public Patch<?> deserialize(JsonParser parser, DeserializationContext ctxt) {
			return new Patch<>(value.deserialize(parser, ctxt));
		}

		@Override
		public Patch<?> getNullValue(DeserializationContext ctxt) {
			return new Patch<>(null);
		}

		@Override
		public Object getAbsentValue(DeserializationContext ctxt) {
			return null;
		}

	}

}

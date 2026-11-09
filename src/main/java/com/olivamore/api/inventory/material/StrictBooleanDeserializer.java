package com.olivamore.api.inventory.material;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Yalnizca JSON {@code true}/{@code false}: Jackson'in sayidan boolean'a cevirmesi ({@code 2 -> true}) kapali. */
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		JsonToken token = parser.currentToken();
		if (token == JsonToken.VALUE_TRUE) {
			return Boolean.TRUE;
		}
		if (token == JsonToken.VALUE_FALSE) {
			return Boolean.FALSE;
		}
		return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
	}

}

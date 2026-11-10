package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisBooleanValueTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void onlyJsonBooleansAreAccepted() {
		assertThat(AnalysisBatchProcessService.booleanValue(JSON.booleanNode(true))).isTrue();
		assertThat(AnalysisBatchProcessService.booleanValue(JSON.booleanNode(false))).isFalse();
	}

	@Test
	void textsNumbersAndNullAreRejected() {
		JsonNode[] invalid = { JSON.stringNode("false"), JSON.stringNode("hayır"), JSON.stringNode("1"),
			JSON.numberNode(1), JSON.numberNode(0), JSON.nullNode(), null };
		Arrays.stream(invalid).forEach(value ->
			assertThatThrownBy(() -> AnalysisBatchProcessService.booleanValue(value))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("Attribute değeri true ya da false olmalıdır."));
	}

}

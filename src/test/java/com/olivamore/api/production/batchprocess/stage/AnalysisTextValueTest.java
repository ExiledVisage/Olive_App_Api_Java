package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTextValueTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void arraysAreRejectedInsteadOfBeingWrittenAsTheWordArray() {
		for (JsonNode array : List.of(JSON.arrayNode().add("a"), JSON.objectNode().put("a", "b"))) {
			assertThatThrownBy(() -> AnalysisBatchProcessService.textValue(array))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("Attribute metin değeri dizi olamaz.");
		}
	}

	@Test
	void scalarsAreWrittenAsText() {
		assertThat(AnalysisBatchProcessService.textValue(JSON.stringNode("metin"))).isEqualTo("metin");
		assertThat(AnalysisBatchProcessService.textValue(JSON.numberNode(new BigDecimal("5.50")))).isEqualTo("5.5");
		assertThat(AnalysisBatchProcessService.textValue(null)).isEmpty();
	}

}

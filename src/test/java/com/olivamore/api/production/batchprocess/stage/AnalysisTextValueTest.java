package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTextValueTest {

	@Test
	void arraysAreRejectedInsteadOfBeingWrittenAsTheWordArray() {
		for (Object array : List.of(List.of("a"), Map.of("a", "b"))) {
			assertThatThrownBy(() -> AnalysisBatchProcessService.scalarText(array))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("Attribute metin değeri dizi olamaz.");
		}
	}

	@Test
	void scalarsPassThrough() {
		assertThat(AnalysisBatchProcessService.scalarText("metin")).isEqualTo("metin");
		assertThat(AnalysisBatchProcessService.scalarText(5L)).isEqualTo(5L);
		assertThat(AnalysisBatchProcessService.scalarText(null)).isNull();
	}

}

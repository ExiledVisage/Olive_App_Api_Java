package com.olivamore.api.product.productattribute;

import com.olivamore.api.product.OliveOilGrade;
import com.olivamore.api.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Yag sinifi on izlemesi (docs/QUALITY.md 1): analiz formu degerleri girerken sinifi ve sinifi dusurenleri verir. */
@RestController
class OliveOilGradeController {

	record GradeRequest(Map<String, BigDecimal> values) {
	}

	record Failure(String key, String label, BigDecimal value, String limit, String blocks) {
	}

	/** @param grade asitlik yoksa null */
	record GradeResponse(String grade, String label, List<Failure> failures, List<OliveOilGrade.Missing> missing) {
	}

	@PostMapping("/api/olive-oil-grade")
	@PreAuthorize("hasAuthority('batch_process.view')")
	DataResponse<GradeResponse> grade(@RequestBody GradeRequest request) {
		Map<String, BigDecimal> values = new HashMap<>();
		if (request.values() != null) {
			request.values().forEach((key, value) -> {
				if (key != null && value != null) {
					values.put(key, value);
				}
			});
		}
		OliveOilGrade.Result result = OliveOilGrade.of(values);
		OliveOilGrade.Grade grade = result.grade();
		return DataResponse.of(new GradeResponse(grade == null ? null : grade.key(),
			grade == null ? null : grade.label(), result.failures().stream().map(f -> new Failure(f.key(), f.label(),
				f.value(), f.limit(), f.blocks().key())).toList(), result.missing()));
	}

}

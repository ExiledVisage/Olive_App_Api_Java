package com.olivamore.api.process.processtypefield;

import com.olivamore.api.process.ProcessTypeFieldResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity'den API cevabina. */
@Component
public class ProcessTypeFieldResponses {

	private final ProcessTypeFieldRepository repository;

	ProcessTypeFieldResponses(ProcessTypeFieldRepository repository) {
		this.repository = repository;
	}

	/** Proses tipine gore, sort_order sirasinda. */
	@Transactional(readOnly = true)
	public Map<Long, List<ProcessTypeFieldResponse>> byProcessType(List<Long> processTypeIds) {
		return repository.findByProcessTypeIdInOrderBySortOrderAscIdAsc(processTypeIds).stream()
			.map(ProcessTypeFieldResponses::of)
			.collect(Collectors.groupingBy(ProcessTypeFieldResponse::processTypeId));
	}

	static ProcessTypeFieldResponse of(ProcessTypeField field) {
		return new ProcessTypeFieldResponse(field.getId(), field.getProcessTypeId(), field.getFieldKey(),
			field.getLabel(), field.getDataType(), field.isRequired(), field.getUnit(), field.getSortOrder(),
			field.isActive(), field.getOptions(), field.getDefaultValue(), field.getCreatedAt(),
			field.getUpdatedAt());
	}

}

package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessTypeResponse;
import com.olivamore.api.process.processtypefield.ProcessTypeFieldResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Entity'den API cevabina; proses alanlari tek sorguda yuklenir. */
@Component
public class ProcessTypeResponses {

	private final ProcessTypeRepository repository;
	private final ProcessTypeFieldResource fields;

	ProcessTypeResponses(ProcessTypeRepository repository, ProcessTypeFieldResource fields) {
		this.repository = repository;
		this.fields = fields;
	}

	/** Bulunamayan id'ler haritada yok. */
	@Transactional(readOnly = true)
	public Map<Long, ProcessTypeResponse> byIds(List<Long> ids) {
		List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
		Map<Long, ProcessTypeResponse> out = new LinkedHashMap<>();
		of(repository.findAllById(present)).forEach(response -> out.put(response.id(), response));
		return out;
	}

	List<ProcessTypeResponse> of(List<ProcessType> types) {
		Map<Long, List<Map<String, Object>>> byType =
			fields.byProcessType(types.stream().map(ProcessType::getId).toList());
		return types.stream().map(type -> of(type, byType.getOrDefault(type.getId(), List.of()))).toList();
	}

	ProcessTypeResponse of(ProcessType type) {
		return of(List.of(type)).getFirst();
	}

	private static ProcessTypeResponse of(ProcessType type, List<Map<String, Object>> typeFields) {
		return new ProcessTypeResponse(type.getId(), type.getName(), type.getDescription(), type.getCategory(),
			type.isActive(), typeFields, type.getCreatedAt(), type.getUpdatedAt());
	}

}

package com.olivamore.api.process.processtype;

import com.olivamore.api.process.ProcessCategory;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProcessTypeService {

	private final ProcessTypeRepository repository;

	ProcessTypeService(ProcessTypeRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	List<ProcessType> all() {
		return repository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	ProcessType get(long id) {
		return find(id);
	}

	@Transactional
	ProcessType create(CreateProcessTypeRequest request) {
		if (repository.existsByName(request.name())) {
			throw nameTaken();
		}
		assertAssignable(request.category());
		return repository.save(new ProcessType(request));
	}

	@Transactional
	ProcessType update(long id, UpdateProcessTypeRequest request) {
		ProcessType type = find(id);
		if (request.name() != null && repository.existsByNameAndIdNot(request.name(), id)) {
			throw nameTaken();
		}
		assertAssignable(request.category());
		type.apply(request);
		return type;
	}

	@Transactional
	void delete(long id) {
		ProcessType type = find(id);
		DeletionGuard.of("'" + type.getName() + "' proses tipi")
			.usedBy("üretim aşaması", repository.countStages(id))
			.usedBy("girilmiş ölçüm", repository.countMeasurements(id))
			.check();
		repository.delete(type);
	}

	/** Proses tipi yoksa 404. */
	@Transactional(readOnly = true)
	public void ensureExists(long id) {
		if (!repository.existsById(id)) {
			throw new ModelNotFoundException("ProcessType", id);
		}
	}

	private ProcessType find(long id) {
		return repository.findById(id).orElseThrow(() -> new ModelNotFoundException("ProcessType", id));
	}

	private static void assertAssignable(ProcessCategory category) {
		if (category != null && !category.assignable()) {
			throw LaravelValidationException.of("category", LaravelMessages.format(LaravelMessages.IN, "category"));
		}
	}

	private static LaravelValidationException nameTaken() {
		return LaravelValidationException.of("name", LaravelMessages.format(LaravelMessages.UNIQUE, "name"));
	}

}

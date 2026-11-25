package com.olivamore.api.quality;

import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BRC/IFS dokuman kaydi (docs/QUALITY.md 4). {@code ?status}, {@code ?review_state} (overdue, soon, ok) suzgec. */
@RestController
@RequestMapping("/api/quality/documents")
class QualityDocumentController {

	private final QualityDocuments documents;

	QualityDocumentController(QualityDocuments documents) {
		this.documents = documents;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('quality.view')")
	ListResponse<QualityDocuments.Document> index(@RequestParam(name = "status", required = false) String status,
		@RequestParam(name = "review_state", required = false) String reviewState) {
		return new ListResponse<>(documents.all(status, reviewState));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('quality.view')")
	DataResponse<QualityDocuments.Document> show(@PathVariable long id) {
		return DataResponse.of(documents.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('quality.manage')")
	ResponseEntity<DataResponse<QualityDocuments.Document>> store(
		@RequestBody QualityDocuments.DocumentRequest request) {
		return DataResponse.created(documents.create(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<QualityDocuments.Document> update(@PathVariable long id,
		@RequestBody QualityDocuments.DocumentRequest request) {
		return DataResponse.of(documents.update(id, request));
	}

	/** Bugun gozden gecirildi. */
	@PostMapping("/{id}/review")
	@PreAuthorize("hasAuthority('quality.manage')")
	DataResponse<QualityDocuments.Document> review(@PathVariable long id,
		@RequestBody(required = false) QualityDocuments.ReviewRequest request) {
		return DataResponse.of(documents.review(id, request == null ? new QualityDocuments.ReviewRequest(null, null)
			: request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('quality.manage')")
	ResponseEntity<Void> destroy(@PathVariable long id) {
		documents.delete(id);
		return ResponseEntity.noContent().build();
	}

}

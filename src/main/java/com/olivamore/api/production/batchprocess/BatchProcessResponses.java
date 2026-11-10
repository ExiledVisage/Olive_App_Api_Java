package com.olivamore.api.production.batchprocess;

import com.olivamore.api.inventory.MaterialResources;
import com.olivamore.api.inventory.MaterialResponse;
import com.olivamore.api.process.ProcessResources;
import com.olivamore.api.process.ProcessTypeFieldResponse;
import com.olivamore.api.process.ProcessTypeResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.BatchSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.Detail;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.FieldSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.FieldValueResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.ProcessTypeSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.ReviewResponse;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.StageSummary;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.UserName;
import com.olivamore.api.production.batchprocess.BatchProcessResponse.UserSummary;
import com.olivamore.api.production.batchprocess.stage.StageQueries;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Field;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Stage;
import com.olivamore.api.productionline.ProductionLineResources;
import com.olivamore.api.productionline.StageExtensionResponse;
import com.olivamore.api.shared.error.ModelNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Proses cevaplari: parti baglaminda ya da detay (gonderen, onaylayan, incelemeler ve uzanti degerleriyle). */
@Component
public class BatchProcessResponses {

	private final BatchProcessRepository processes;
	private final BatchProcessFieldValueRepository fieldValues;
	private final BatchProcessReviewRepository reviews;
	private final BatchProcessService service;
	private final StageQueries stages;
	private final ProcessResponseQueries queries;
	private final ProcessResources processTypes;
	private final ProductionLineResources lines;
	private final MaterialResources materials;

	BatchProcessResponses(BatchProcessRepository processes, BatchProcessFieldValueRepository fieldValues,
		BatchProcessReviewRepository reviews, BatchProcessService service, StageQueries stages,
		ProcessResponseQueries queries, ProcessResources processTypes, ProductionLineResources lines,
		MaterialResources materials) {
		this.processes = processes;
		this.fieldValues = fieldValues;
		this.reviews = reviews;
		this.service = service;
		this.stages = stages;
		this.queries = queries;
		this.processTypes = processTypes;
		this.lines = lines;
		this.materials = materials;
	}

	/** Parti baglaminda: {@code requires_approval} hesaplanmaz. */
	public List<BatchProcessResponse> ofBatchProcesses(List<BatchProcess> rows) {
		return map(rows, null);
	}

	/** Proses detayi: {@code requires_approval} hesaplanmis deger. */
	BatchProcessResponse detail(long id) {
		BatchProcess process = processes.findById(id).orElseThrow(() -> new ModelNotFoundException("BatchProcess", id));
		return map(List.of(process), service.requiresApproval(process)).getFirst();
	}

	/** @param requiresApproval {@code null}: parti baglami; degilse detay */
	private List<BatchProcessResponse> map(List<BatchProcess> rows, Boolean requiresApproval) {
		boolean detail = requiresApproval != null;
		MaterialResources.Load load = detail ? MaterialResources.Load.FULL : MaterialResources.Load.TYPE_AND_ATTRIBUTES;
		List<Long> ids = rows.stream().map(BatchProcess::getId).toList();
		Map<Long, BatchSummary> batches = queries.batches(
			rows.stream().map(BatchProcess::getProductionBatchId).distinct().toList());
		Map<Long, UserSummary> operators = queries.users(present(rows.stream().map(BatchProcess::getOperatorId)));
		Map<Long, Stage> stageById = stages.stages(rows.stream().map(BatchProcess::getProductionStageId).distinct().toList());
		Map<Long, ProcessTypeResponse> types = processTypes.processTypesById(
			present(stageById.values().stream().map(Stage::processTypeId)));
		Map<Long, List<ProcessTypeFieldResponse>> typeFields = processTypes.processTypeFields(new ArrayList<>(types.keySet()));
		Map<Long, List<StageExtensionResponse>> extensions = lines.stageExtensions(new ArrayList<>(stageById.keySet()), detail);
		Map<Long, List<FieldValueResponse>> values = fieldValues(ids);
		Map<Long, List<ReviewResponse>> reviewRows = detail ? reviews(ids) : Map.of();
		Map<Long, UserSummary> reviewers = detail ? queries.users(present(rows.stream()
			.flatMap(p -> Stream.of(p.getSubmittedByUserId(), p.getApprovedByUserId())))) : Map.of();
		Map<Long, List<Long>> createdIds = queries.materialsCreatedBy(ids);
		Map<Long, List<Long>> closedIds = queries.materialsClosedBy(ids);
		Map<Long, MaterialResponse> materialById = materialsById(createdIds, closedIds, load);

		List<BatchProcessResponse> out = new ArrayList<>();
		for (BatchProcess p : rows) {
			Stage stage = stageById.get(p.getProductionStageId());
			ProcessTypeResponse type = stage.processTypeId() == null ? null : types.get(stage.processTypeId());
			out.add(new BatchProcessResponse(p.getId(), p.getProductionBatchId(), p.getProductionStageId(), p.getOperatorId(),
				p.getStatus(), p.getStartedAt(), p.getCompletedAt(), p.getNote(), p.getOperationData(), requiresApproval,
				p.getSubmittedAt(), p.getApprovedAt(),
				!detail ? null : new Detail(name(reviewers.get(p.getSubmittedByUserId())),
					name(reviewers.get(p.getApprovedByUserId())), reviewRows.getOrDefault(p.getId(), List.of())),
				batches.get(p.getProductionBatchId()), operators.get(p.getOperatorId()),
				new StageSummary(stage.id(), stage.name(), stage.stageOrder(), stage.isRequired()),
				type == null ? null : new ProcessTypeSummary(type.id(), type.name(), type.description(), type.category()),
				type == null ? List.of() : typeFields.getOrDefault(type.id(), List.of()),
				extensions.getOrDefault(stage.id(), List.of()),
				values.getOrDefault(p.getId(), List.of()),
				materials(createdIds.get(p.getId()), materialById),
				materials(closedIds.get(p.getId()), materialById),
				p.getCreatedAt(), p.getUpdatedAt()));
		}
		return out;
	}

	private static List<Long> present(Stream<Long> ids) {
		return ids.filter(Objects::nonNull).distinct().toList();
	}

	private Map<Long, MaterialResponse> materialsById(Map<Long, List<Long>> created, Map<Long, List<Long>> closed,
		MaterialResources.Load load) {
		List<Long> ids = Stream.concat(created.values().stream(), closed.values().stream())
			.flatMap(Collection::stream).distinct().toList();
		Map<Long, MaterialResponse> out = new LinkedHashMap<>();
		materials.materials(ids, load).forEach(m -> out.put(m.id(), m));
		return out;
	}

	private static List<MaterialResponse> materials(List<Long> ids, Map<Long, MaterialResponse> byId) {
		return ids == null ? List.of() : ids.stream().map(byId::get).toList();
	}

	private static UserName name(UserSummary user) {
		return user == null ? null : new UserName(user.id(), user.name());
	}

	private Map<Long, List<ReviewResponse>> reviews(List<Long> processIds) {
		List<BatchProcessReview> rows = reviews.findByBatchProcessIdInOrderByReviewedAtAscIdAsc(processIds);
		Map<Long, UserSummary> users = queries.users(present(rows.stream()
			.flatMap(r -> Stream.of(r.getReviewedByUserId(), r.getSubmittedByUserId()))));
		return rows.stream().collect(Collectors.groupingBy(BatchProcessReview::getBatchProcessId, LinkedHashMap::new,
			Collectors.mapping(r -> new ReviewResponse(r.getId(), r.getDecision(), r.getNote(), r.getOperationData(),
				r.getReviewedAt(), name(users.get(r.getReviewedByUserId())), name(users.get(r.getSubmittedByUserId()))),
				Collectors.toList())));
	}

	private Map<Long, List<FieldValueResponse>> fieldValues(List<Long> processIds) {
		List<BatchProcessFieldValue> rows = fieldValues.findByBatchProcessIdIn(processIds);
		Map<Long, Field> fields = stages.fields(rows.stream().map(BatchProcessFieldValue::getProcessTypeFieldId)
			.distinct().toList());
		return rows.stream().collect(Collectors.groupingBy(BatchProcessFieldValue::getBatchProcessId, LinkedHashMap::new,
			Collectors.mapping(v -> response(v, fields.get(v.getProcessTypeFieldId())), Collectors.toList())));
	}

	private static FieldValueResponse response(BatchProcessFieldValue v, Field f) {
		return new FieldValueResponse(v.getId(), v.getBatchProcessId(), v.getProcessTypeFieldId(), v.getValueText(),
			v.getValueDecimal(), v.getValueInteger(), v.getValueBoolean(), v.getValueDatetime(), v.getValueJson(), v.resolved(),
			new FieldSummary(f.id(), f.processTypeId(), f.fieldKey(), f.label(), f.dataType(), f.isRequired(), f.unit(),
				f.sortOrder(), f.isActive()));
	}

}

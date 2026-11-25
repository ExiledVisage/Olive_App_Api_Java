package com.olivamore.api.quality;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.error.ValidationFailedException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.MessageKeys;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Geri cagirma ve izlenebilirlik tatbikati (docs/QUALITY.md 3). Olusturulurken ileri iz ({@link RecallTrace})
 * hesaplanir, ozeti kayda ve siparisleri satirlara yazilir; yeniden hesaplamada aranan ya da geri donen satirlar
 * korunur. Tatbikatin hedefi 4 saat; tamamlaninca sure hedefe gore gecti / kaldi.
 */
@Service
class Recalls {

	static final int DRILL_TARGET_MINUTES = 240;

	static final Set<String> SOURCES = Set.of("delivery", "tank_material", "run");

	record User(Long id, String name) {
	}

	record Summary(int runCount, int orderCount, int customerCount, int definiteOrders, int possibleOrders,
		int quantity, int contactedOrders, int returnedQuantity) {
	}

	/** @param result tatbikatta passed / failed (tamamlaninca); geri cagirmada null */
	record Recall(long id, String code, String kind, String sourceType, long sourceId, String sourceLabel,
		String reason, String status, LocalDateTime startedAt, LocalDateTime completedAt, Long durationMinutes,
		Integer targetMinutes, String result, String findings, User createdBy, LocalDateTime tracedAt,
		Summary summary) {
	}

	record OrderRow(long id, long orderId, String orderStatus, long customerId, String customer, String phone,
		long finishedProductId, String product, int quantity, String certainty, LocalDateTime contactedAt,
		Integer returnedQuantity, String note) {
	}

	record Detail(Recall recall, JsonNode trace, List<OrderRow> orders) {
	}

	record CreateRequest(String kind, String sourceType, Long sourceId, String reason, LocalDateTime startedAt) {
	}

	record OrderUpdate(Boolean contacted, Integer returnedQuantity, String note) {
	}

	record CompleteRequest(String findings) {
	}

	private record RecallRow(long id, String code, String kind, String sourceType, long sourceId, String reason,
		String status, LocalDateTime startedAt, LocalDateTime completedAt, Integer targetMinutes, String findings,
		String trace, LocalDateTime tracedAt, Long userId, String userName) {
	}

	private static final String SELECT = """
		select r.id, r.code, r.kind, r.source_type, r.source_id, r.reason, r.status, r.started_at, r.completed_at,
			r.target_minutes, r.findings, r.trace::text as trace, r.traced_at, r.created_by_user_id as user_id,
			u.name as user_name
		from recalls r left join users u on u.id = r.created_by_user_id
		""";

	private static final String ORDERS = """
		select ro.id, ro.order_id, o.status as order_status, c.id as customer_id,
			case when coalesce(c.company_name, '') in ('', '0') then c.name else c.company_name end as customer,
			c.phone, ro.finished_product_id, p.name as product, ro.quantity, ro.certainty, ro.contacted_at,
			ro.returned_quantity, ro.note
		from recall_orders ro
		join orders o on o.id = ro.order_id
		join customers c on c.id = o.customer_id
		join finished_products p on p.id = ro.finished_product_id
		""";

	private final JdbcClient jdbc;
	private final RecallTrace tracer;
	private final JsonMapper json;

	Recalls(JdbcClient jdbc, RecallTrace tracer, JsonMapper json) {
		this.jdbc = jdbc;
		this.tracer = tracer;
		this.json = json;
	}

	/** En yeni once; {@code kind} ve {@code status} suzgec. */
	@Transactional(readOnly = true)
	List<Recall> all(String kind, String status) {
		List<RecallRow> rows = jdbc.sql(SELECT + """
			 where (cast(:kind as varchar) is null or r.kind = :kind)
				and (cast(:status as varchar) is null or r.status = :status)
			order by r.started_at desc, r.id desc
			""").param("kind", kind).param("status", status).query(RecallRow.class).list();
		Map<Long, List<OrderRow>> orders = rows.isEmpty() ? Map.of()
			: ordersOf(rows.stream().map(RecallRow::id).toList());
		return rows.stream().map(r -> recall(r, orders.getOrDefault(r.id(), List.of()))).toList();
	}

	@Transactional(readOnly = true)
	Detail get(long id) {
		RecallRow row = row(id);
		List<OrderRow> orders = ordersOf(List.of(id)).getOrDefault(id, List.of());
		return new Detail(recall(row, orders), row.trace() == null ? null : json.readTree(row.trace()), orders);
	}

	@Transactional
	Detail create(CreateRequest request, long userId) {
		ValidationErrors errors = new ValidationErrors();
		if (!"recall".equals(request.kind()) && !"drill".equals(request.kind())) {
			errors.add("kind", MessageKeys.IN);
		}
		if (request.sourceType() == null || !SOURCES.contains(request.sourceType())) {
			errors.add("source_type", MessageKeys.IN);
		}
		if (request.sourceId() == null) {
			errors.add("source_id", MessageKeys.REQUIRED);
		}
		if (request.reason() == null || request.reason().isBlank()) {
			errors.add("reason", MessageKeys.REQUIRED);
		}
		LocalDateTime now = Timestamps.now();
		if (request.startedAt() != null && request.startedAt().isAfter(now.plusMinutes(5))) {
			errors.add("started_at", "Başlangıç ileri bir tarih olamaz.");
		}
		errors.throwIfAny();
		tracer.sourceLabel(request.sourceType(), request.sourceId());

		boolean drill = "drill".equals(request.kind());
		String prefix = (drill ? "IT-" : "GC-") + now.getYear() + "-";
		long next = jdbc.sql("select count(*) from recalls where code like :prefix").param("prefix", prefix + "%")
			.query(Long.class).single() + 1;
		long id = jdbc.sql("""
			insert into recalls (code, kind, source_type, source_id, reason, status, started_at, target_minutes,
				created_by_user_id, created_at, updated_at)
			values (:code, :kind, :sourceType, :sourceId, :reason, 'open', :startedAt, :target, :user, :now, :now)
			returning id
			""").param("code", prefix + "%03d".formatted(next)).param("kind", request.kind())
			.param("sourceType", request.sourceType()).param("sourceId", request.sourceId())
			.param("reason", request.reason().trim())
			.param("startedAt", request.startedAt() != null ? request.startedAt() : now)
			.param("target", drill ? DRILL_TARGET_MINUTES : null).param("user", userId).param("now", now)
			.query(Long.class).single();
		retrace(id);
		return get(id);
	}

	/** Iz yeniden hesaplanir; aranan ya da geri donen siparis satirlari izden ciksa da kalir. */
	@Transactional
	Detail retrace(long id) {
		RecallRow row = row(id);
		requireOpen(row);
		RecallTrace.Result result = tracer.trace(row.sourceType(), row.sourceId());
		LocalDateTime now = Timestamps.now();
		jdbc.sql("update recalls set trace = cast(:trace as jsonb), traced_at = :now, updated_at = :now where id = :id")
			.param("trace", json.writeValueAsString(result)).param("now", now).param("id", id).update();
		Set<String> keep = new HashSet<>();
		for (RecallTrace.OrderLine line : result.orders()) {
			keep.add(line.orderId() + ":" + line.productId());
			jdbc.sql("""
				insert into recall_orders (recall_id, order_id, finished_product_id, quantity, certainty, created_at,
					updated_at)
				values (:recall, :order, :product, :quantity, :certainty, :now, :now)
				on conflict (recall_id, order_id, finished_product_id) do update set quantity = excluded.quantity,
					certainty = excluded.certainty, updated_at = excluded.updated_at
				""").param("recall", id).param("order", line.orderId()).param("product", line.productId())
				.param("quantity", line.quantity()).param("certainty", line.definite() ? "definite" : "possible")
				.param("now", now).update();
		}
		jdbc.sql("""
			select id, order_id || ':' || finished_product_id from recall_orders
			where recall_id = :id and contacted_at is null and returned_quantity is null
			""").param("id", id).query((rs, n) -> Map.entry(rs.getLong(1), rs.getString(2))).list().stream()
			.filter(e -> !keep.contains(e.getValue()))
			.forEach(e -> jdbc.sql("delete from recall_orders where id = :id").param("id", e.getKey()).update());
		return get(id);
	}

	@Transactional
	Detail updateOrder(long recallOrderId, OrderUpdate request) {
		Long recallId = jdbc.sql("select recall_id from recall_orders where id = :id").param("id", recallOrderId)
			.query(Long.class).optional().orElseThrow(() -> new ModelNotFoundException("RecallOrder", recallOrderId));
		requireOpen(row(recallId));
		Integer quantity = jdbc.sql("select quantity from recall_orders where id = :id").param("id", recallOrderId)
			.query(Integer.class).single();
		if (request.returnedQuantity() != null && (request.returnedQuantity() < 0
			|| request.returnedQuantity() > quantity)) {
			throw ValidationFailedException.of("returned_quantity",
				"Geri gelen adet 0 ile " + quantity + " arasında olmalıdır.");
		}
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update recall_orders set
				contacted_at = case when cast(:contacted as boolean) is null then contacted_at
					when :contacted then coalesce(contacted_at, :now) else null end,
				returned_quantity = coalesce(:returned, returned_quantity),
				note = coalesce(:note, note), updated_at = :now
			where id = :id
			""").param("contacted", request.contacted()).param("returned", request.returnedQuantity())
			.param("note", request.note() == null || request.note().isBlank() ? null : request.note().trim())
			.param("now", now).param("id", recallOrderId).update();
		return get(recallId);
	}

	@Transactional
	Detail complete(long id, CompleteRequest request) {
		RecallRow row = row(id);
		requireOpen(row);
		LocalDateTime now = Timestamps.now();
		jdbc.sql("""
			update recalls set status = 'completed', completed_at = :now, findings = coalesce(:findings, findings),
				updated_at = :now
			where id = :id
			""").param("now", now).param("findings", request == null || request.findings() == null
				|| request.findings().isBlank() ? null : request.findings().trim()).param("id", id).update();
		return get(id);
	}

	@Transactional
	Detail cancel(long id) {
		requireOpen(row(id));
		jdbc.sql("update recalls set status = 'cancelled', updated_at = :now where id = :id")
			.param("now", Timestamps.now()).param("id", id).update();
		return get(id);
	}

	private RecallRow row(long id) {
		return jdbc.sql(SELECT + " where r.id = :id").param("id", id).query(RecallRow.class).optional()
			.orElseThrow(() -> new ModelNotFoundException("Recall", id));
	}

	private static void requireOpen(RecallRow row) {
		if (!"open".equals(row.status())) {
			throw new BusinessRuleException("Kapanmış kayıt değiştirilemez.");
		}
	}

	private Map<Long, List<OrderRow>> ordersOf(List<Long> recallIds) {
		record Keyed(long recallId, OrderRow row) {
		}
		return jdbc.sql(ORDERS.replace("select ro.id,", "select ro.recall_id, ro.id,")
				+ " where ro.recall_id in (:ids) order by ro.certainty, customer, ro.order_id")
			.param("ids", recallIds)
			.query((rs, n) -> new Keyed(rs.getLong("recall_id"), new OrderRow(rs.getLong("id"), rs.getLong("order_id"),
				rs.getString("order_status"), rs.getLong("customer_id"), rs.getString("customer"),
				rs.getString("phone"), rs.getLong("finished_product_id"), rs.getString("product"),
				rs.getInt("quantity"), rs.getString("certainty"),
				rs.getObject("contacted_at", LocalDateTime.class),
				(Integer) rs.getObject("returned_quantity"), rs.getString("note"))))
			.list().stream().collect(Collectors.groupingBy(Keyed::recallId,
				Collectors.mapping(Keyed::row, Collectors.toList())));
	}

	private Recall recall(RecallRow row, List<OrderRow> orders) {
		int runs = 0;
		if (row.trace() != null) {
			JsonNode runsNode = json.readTree(row.trace()).get("runs");
			runs = runsNode == null ? 0 : runsNode.size();
		}
		Summary summary = new Summary(runs, (int) orders.stream().map(OrderRow::orderId).distinct().count(),
			(int) orders.stream().map(OrderRow::customerId).distinct().count(),
			(int) orders.stream().filter(o -> "definite".equals(o.certainty())).map(OrderRow::orderId).distinct()
				.count(),
			(int) orders.stream().filter(o -> "possible".equals(o.certainty())).map(OrderRow::orderId).distinct()
				.count(),
			orders.stream().mapToInt(OrderRow::quantity).sum(),
			(int) orders.stream().filter(o -> o.contactedAt() != null).map(OrderRow::orderId).distinct().count(),
			orders.stream().map(OrderRow::returnedQuantity).filter(Objects::nonNull)
				.mapToInt(Integer::intValue).sum());
		LocalDateTime end = row.completedAt() != null ? row.completedAt()
			: "open".equals(row.status()) ? Timestamps.now() : null;
		Long minutes = end == null ? null : Math.max(0, Duration.between(row.startedAt(), end).toMinutes());
		String result = null;
		if ("drill".equals(row.kind()) && "completed".equals(row.status()) && row.targetMinutes() != null) {
			result = minutes != null && minutes <= row.targetMinutes() ? "passed" : "failed";
		}
		return new Recall(row.id(), row.code(), row.kind(), row.sourceType(), row.sourceId(),
			sourceLabel(row), row.reason(), row.status(), row.startedAt(), row.completedAt(), minutes,
			row.targetMinutes(), result, row.findings(), row.userId() == null ? null
				: new User(row.userId(), row.userName()), row.tracedAt(), summary);
	}

	private String sourceLabel(RecallRow row) {
		if (row.trace() != null) {
			JsonNode source = json.readTree(row.trace()).get("source");
			if (source != null && !source.isNull()) {
				return source.asString();
			}
		}
		return null;
	}

}

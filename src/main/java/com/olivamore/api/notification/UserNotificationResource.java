package com.olivamore.api.notification;

import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Laravel {@code UserNotificationResource}. */
@Component
class UserNotificationResource {

	private final Db db;

	UserNotificationResource(Db db) {
		this.db = db;
	}

	/** UserNotificationResource. @param withSender {@code sentByUser} yuklu mu */
	List<Map<String, Object>> of(List<Row> rows, boolean withSender) {
		Map<Long, Row> senders = withSender
			? Relations.byId(db.whereIn("users", "id", Relations.column(rows, "sent_by_user_id"), null))
			: Map.of();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Row n : rows) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", n.get("id"));
			r.put("type", n.get("type"));
			r.put("title", n.get("title"));
			r.put("body", n.get("body"));
			r.put("subject_type", n.get("subject_type"));
			r.put("subject_id", n.get("subject_id") == null ? null : Php.intval(n.get("subject_id")));
			r.put("is_read", n.get("read_at") != null);
			r.put("read_at", n.get("read_at"));
			r.put("created_at", n.get("created_at"));
			if (withSender) {
				Row sender = n.longValue("sent_by_user_id") == null ? null : senders.get(n.longValue("sent_by_user_id"));
				if (sender == null) {
					r.put("sent_by", null);
				}
				else {
					Map<String, Object> s = new LinkedHashMap<>();
					s.put("id", sender.get("id"));
					s.put("name", sender.get("name"));
					r.put("sent_by", s);
				}
			}
			out.add(r);
		}
		return out;
	}

}

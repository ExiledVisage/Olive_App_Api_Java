package com.olivamore.api.analytics.report;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/** Kullanicinin kaydettigi rapor secimi. */
@Entity
@Table(name = "analytics_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AnalyticsReport extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private JsonNode config;

	AnalyticsReport(long userId, String name, JsonNode config) {
		this.userId = userId;
		this.name = name;
		this.config = config;
	}

	void rename(String newName) {
		name = newName;
	}

	void configure(JsonNode newConfig) {
		config = newConfig;
	}

}

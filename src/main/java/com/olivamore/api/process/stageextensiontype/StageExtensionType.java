package com.olivamore.api.process.stageextensiontype;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tipler seed ile gelir; API yalnizca okur. */
@Entity
@Table(name = "stage_extension_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StageExtensionType extends AuditedEntity {

	@Id
	private Long id;

	@Column(nullable = false)
	private String key;

	@Column(nullable = false)
	private String name;

	private String description;

}

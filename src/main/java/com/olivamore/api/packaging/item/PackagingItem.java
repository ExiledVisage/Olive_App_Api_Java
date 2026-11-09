package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ambalaj kalemi: sise, kapak, etiket. {@code currentStock} lotlarin kalan toplaminin onbellegi. */
@Entity
@Table(name = "packaging_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PackagingItem extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	@Convert(converter = PackagingItemTypeConverter.class)
	private PackagingItemType type;

	@Column(nullable = false)
	private int currentStock;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	PackagingItem(CreatePackagingItemRequest request) {
		name = request.name();
		type = request.type();
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	/** Gelmeyen alanlar degismez; eldeki adet buradan degismez. */
	void apply(UpdatePackagingItemRequest request) {
		if (request.name() != null) {
			name = request.name();
		}
		if (request.type() != null) {
			type = request.type();
		}
		if (request.isActive() != null) {
			active = request.isActive();
		}
	}

	public void refreshStock(int total) {
		currentStock = total;
	}

}

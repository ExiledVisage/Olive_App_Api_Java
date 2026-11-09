package com.olivamore.api.sales.order;

import com.olivamore.api.shared.persistence.AuditedEntity;
import com.olivamore.api.shared.time.Timestamps;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OrderStatusHistory extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long orderId;

	@Convert(converter = OrderStatus.StatusConverter.class)
	private OrderStatus fromStatus;

	@Column(nullable = false)
	@Convert(converter = OrderStatus.StatusConverter.class)
	private OrderStatus toStatus;

	private Long changedByUserId;

	private String note;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	OrderStatusHistory(long orderId, OrderStatus from, OrderStatus to, long userId, String note) {
		this.orderId = orderId;
		fromStatus = from;
		toStatus = to;
		changedByUserId = userId;
		this.note = note;
		changedAt = Timestamps.now();
	}

}

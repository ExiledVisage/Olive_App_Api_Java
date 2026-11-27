package com.olivamore.api.sales.order;

import com.olivamore.api.sales.order.OrderStockQueries.ProductRow;
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

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OrderItem extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long orderId;

	@Column(nullable = false)
	private Long finishedProductId;

	@Column(nullable = false)
	private int quantity;

	/** KDV dahil birim fiyat; bos: faturada urunun liste fiyati (docs/INVOICES.md). */
	private BigDecimal unitPrice;

	/** Kalem yazilirken urunden alinan KDV yuzdesi. */
	@Column(nullable = false)
	private int vatRate;

	/** Fiyat verilmezse urunun o anki liste fiyati. */
	OrderItem(long orderId, OrderItemRequest request, ProductRow product) {
		this.orderId = orderId;
		finishedProductId = request.finishedProductId();
		quantity = request.quantity();
		unitPrice = request.unitPrice() != null ? request.unitPrice() : product.salePrice();
		vatRate = product.vatRate();
	}

	/** KDV dahil kalem tutari; fiyat yoksa bos. */
	BigDecimal lineTotal() {
		return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

}

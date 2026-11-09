package com.olivamore.api.sales.order;

import com.olivamore.api.sales.customer.Customer;
import com.olivamore.api.sales.customer.CustomerAddress;
import com.olivamore.api.shared.json.Patch;
import com.olivamore.api.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** {@code orders} tablosu; {@code Order} JPQL'de ayrilmis kelime oldugu icin bu ad. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SalesOrder extends AuditedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delivery_address_id")
	private CustomerAddress deliveryAddress;

	private Long createdByUserId;

	@Column(nullable = false)
	@Convert(converter = OrderStatus.StatusConverter.class)
	private OrderStatus status;

	@Column(nullable = false)
	private LocalDate orderDate;

	private String note;

	SalesOrder(Customer customer, CustomerAddress address, LocalDate orderDate, String note, long userId) {
		this.customer = customer;
		deliveryAddress = address;
		this.orderDate = orderDate;
		this.note = note;
		createdByUserId = userId;
		status = OrderStatus.DRAFT;
	}

	void changeCustomer(Customer value) {
		customer = value;
	}

	void changeAddress(CustomerAddress value) {
		deliveryAddress = value;
	}

	void changeOrderDate(LocalDate value) {
		orderDate = value;
	}

	void changeNote(Patch<String> value) {
		Patch.ifPresent(value, text -> note = text);
	}

	void changeStatus(OrderStatus value) {
		status = value;
	}

	boolean addressBelongsToCustomer() {
		return deliveryAddress.getCustomerId().equals(customer.getId());
	}

}

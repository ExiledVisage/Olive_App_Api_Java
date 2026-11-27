package com.olivamore.api.sales;

import com.olivamore.api.shared.time.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Site ve pazar yeri siparislerinin sales'e girdigi kapi (docs/INTEGRATIONS.md): musteri e-posta, yoksa telefonla
 * bulunur ya da olusur; teslimat adresi musteride yoksa eklenir; siparis taslak olarak, kaynagi ve dis numarasiyla
 * yazilir. Kalemlerin urunleri cagiran tarafindan eslestirilmis olmalidir; ayni urunun kalemleri birlesir (adetler
 * toplanir, fiyat agirlikli ortalama). Fiyati gelmeyen kalem urunun liste fiyatini, her kalem urunun KDV oranini alir.
 */
@Component
public class ExternalOrderIntake {

	static final Set<String> SOURCES = Set.of("woocommerce", "trendyol", "hepsiburada");

	/** @param externalRef musterinin gordugu numara ("1234") */
	public record IncomingOrder(String source, String externalRef, LocalDate orderDate, Buyer buyer,
		Delivery delivery, List<Item> items, String note) {
	}

	public record Buyer(String name, String company, String email, String phone) {
	}

	public record Delivery(String country, String city, String district, String line, String postalCode) {
	}

	/** @param unitPrice KDV dahil, indirim sonrasi birim fiyat; bos olabilir */
	public record Item(long finishedProductId, int quantity, BigDecimal unitPrice) {
	}

	private final JdbcClient jdbc;

	ExternalOrderIntake(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Siparisi yazar, id'sini dondurur; cagiranin transaction'ina katilir. */
	@Transactional(propagation = Propagation.MANDATORY)
	public long receive(IncomingOrder order) {
		if (!SOURCES.contains(order.source())) {
			throw new IllegalArgumentException("Bilinmeyen kaynak: " + order.source());
		}
		if (order.items().isEmpty()) {
			throw new IllegalArgumentException("Kalemsiz siparis alinmaz");
		}
		LocalDateTime now = Timestamps.now();
		long customerId = findCustomer(order.buyer())
			.orElseGet(() -> createCustomer(order.buyer(), order.source(), now));
		long addressId = address(customerId, order.delivery(), now);
		String label = label(order.source());
		long orderId = jdbc.sql("""
			insert into orders (customer_id, delivery_address_id, status, order_date, note, source, external_ref,
				created_at, updated_at)
			values (:customer, :address, 'draft', :date, :note, :source, :ref, :now, :now)
			returning id
			""").param("customer", customerId).param("address", addressId).param("date", order.orderDate())
			.param("note", order.note()).param("source", order.source()).param("ref", order.externalRef())
			.param("now", now).query(Long.class).single();
		for (Item item : merged(order.items())) {
			jdbc.sql("""
				insert into order_items (order_id, finished_product_id, quantity, unit_price, vat_rate, created_at,
					updated_at)
				select :order, p.id, :quantity, coalesce(cast(:price as numeric), p.sale_price), p.vat_rate, :now, :now
				from finished_products p where p.id = :product
				""").param("order", orderId).param("product", item.finishedProductId())
				.param("quantity", item.quantity()).param("price", item.unitPrice()).param("now", now).update();
		}
		jdbc.sql("""
			insert into order_status_histories (order_id, from_status, to_status, note, changed_at, created_at,
				updated_at)
			values (:order, null, 'draft', :note, :now, :now, :now)
			""").param("order", orderId).param("note", label + " siparişi #" + order.externalRef() + " alındı.")
			.param("now", now).update();
		return orderId;
	}

	/** Ayni urunun kalemleri tek kalem: adetler toplanir; fiyatlarin hepsi biliniyorsa agirlikli ortalama. */
	static List<Item> merged(List<Item> items) {
		Map<Long, List<Item>> byProduct = new LinkedHashMap<>();
		items.forEach(item -> byProduct.computeIfAbsent(item.finishedProductId(), k -> new ArrayList<>()).add(item));
		List<Item> result = new ArrayList<>();
		byProduct.forEach((product, group) -> {
			int quantity = group.stream().mapToInt(Item::quantity).sum();
			boolean priced = group.stream().allMatch(i -> i.unitPrice() != null);
			BigDecimal price = null;
			if (priced && quantity > 0) {
				BigDecimal total = group.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
				price = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
			}
			result.add(new Item(product, quantity, price));
		});
		return result;
	}

	/** E-posta, yoksa telefon (yalniz rakamlar) ile var olan aktif musteri. */
	private Optional<Long> findCustomer(Buyer buyer) {
		if (buyer.email() != null) {
			Optional<Long> byEmail = jdbc.sql("""
				select id from customers where lower(email) = lower(:email) order by is_active desc, id limit 1
				""").param("email", buyer.email().trim()).query(Long.class).optional();
			if (byEmail.isPresent()) {
				return byEmail;
			}
		}
		String digits = buyer.phone() == null ? "" : buyer.phone().replaceAll("\\D", "");
		if (digits.length() >= 10) {
			return jdbc.sql("""
				select id from customers where right(regexp_replace(coalesce(phone, ''), '\\D', '', 'g'), 10) = :phone
				order by is_active desc, id limit 1
				""").param("phone", digits.substring(digits.length() - 10)).query(Long.class).optional();
		}
		return Optional.empty();
	}

	private long createCustomer(Buyer buyer, String source, LocalDateTime now) {
		return jdbc.sql("""
			insert into customers (name, company_name, phone, email, notes, is_active, created_at, updated_at)
			values (:name, :company, :phone, :email, :notes, true, :now, :now)
			returning id
			""").param("name", cut(buyer.name(), 255)).param("company", cut(buyer.company(), 255))
			.param("phone", cut(buyer.phone(), 255)).param("email", cut(buyer.email(), 255))
			.param("notes", label(source) + " siparişiyle eklendi.").param("now", now).query(Long.class).single();
	}

	/** Ayni adres (sehir + satir) musteride varsa o, yoksa yeni adres. */
	private long address(long customerId, Delivery delivery, LocalDateTime now) {
		String city = delivery.city() == null ? "-" : delivery.city();
		String line = delivery.line();
		Optional<Long> existing = jdbc.sql("""
			select id from customer_addresses
			where customer_id = :customer and lower(city) = lower(:city)
				and lower(coalesce(full_address, '')) = lower(coalesce(:line, ''))
			order by id limit 1
			""").param("customer", customerId).param("city", city).param("line", line).query(Long.class).optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		boolean first = jdbc.sql("select count(*) from customer_addresses where customer_id = :customer")
			.param("customer", customerId).query(Long.class).single() == 0;
		return jdbc.sql("""
			insert into customer_addresses (customer_id, label, country, city, district, postal_code, full_address,
				is_default, created_at, updated_at)
			values (:customer, 'Teslimat', :country, :city, :district, :postal, :line, :default, :now, :now)
			returning id
			""").param("customer", customerId).param("country", delivery.country() == null ? "TR" : delivery.country())
			.param("city", cut(city, 255)).param("district", cut(delivery.district(), 255))
			.param("postal", cut(delivery.postalCode(), 255)).param("line", line).param("default", first)
			.param("now", now).query(Long.class).single();
	}

	private static String label(String source) {
		return switch (source) {
			case "woocommerce" -> "Site";
			case "trendyol" -> "Trendyol";
			case "hepsiburada" -> "Hepsiburada";
			default -> source;
		};
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}

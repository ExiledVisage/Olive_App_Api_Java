package com.olivamore.api.integration.order;

import com.olivamore.api.integration.connection.Connections;
import com.olivamore.api.integration.provider.ExternalOrder;
import com.olivamore.api.integration.provider.Provider;
import com.olivamore.api.integration.provider.ShippingLabel;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/**
 * Pazar yeri siparisinin kargo etiketi (docs/INTEGRATIONS.md): bizdeki siparisten platformdaki pakete gidilir,
 * etiket platformdan alinir (saklanmaz; her istekte taze). Paket hazirlaniyor bildirilmis olmali.
 */
@Service
class ShippingLabels {

	/** @param platform pazar yerinin adi (Trendyol) */
	record Label(String platform, String cargoCompany, String trackingNumber, String format, List<String> labels) {
	}

	private record Target(String provider, long connectionId, String payload, String platformStatus,
		String platformRef) {
	}

	private static final Set<String> REPORTED = Set.of("preparing", "invoiced", "invoice_sent");

	private static final JsonMapper PAYLOAD = JsonMapper.builder().build();

	private final JdbcClient jdbc;
	private final Connections connections;

	ShippingLabels(JdbcClient jdbc, Connections connections) {
		this.jdbc = jdbc;
		this.connections = connections;
	}

	Label label(long salesOrderId) {
		Target target = jdbc.sql("""
			select c.provider, io.connection_id, io.payload::text as payload, io.platform_status, io.platform_ref
			from integration_orders io join integration_connections c on c.id = io.connection_id
			where io.order_id = :order and io.status = 'imported'
			order by io.id desc limit 1
			""").param("order", salesOrderId).query(Target.class).optional()
			.orElseThrow(() -> new BusinessRuleException("Bu sipariş bir pazar yerinden gelmedi; etiket yok."));
		Provider provider = Provider.of(target.provider()).orElseThrow();
		if (!provider.printsLabel()) {
			throw new BusinessRuleException(provider.label() + " siparişinde kargo etiketi alınmaz.");
		}
		if (target.platformStatus() == null || !REPORTED.contains(target.platformStatus())) {
			throw new BusinessRuleException("Etiket için sipariş önce onaylanmalı ve " + provider.label()
				+ "'a hazırlanıyor bildirilmeli.");
		}
		ExternalOrder order = PAYLOAD.readValue(target.payload(), ExternalOrder.class);
		ShippingLabel label = connections.client(connections.connection(target.connectionId()))
			.label(order, target.platformRef());
		return new Label(provider.label(), label.cargoCompany(), label.trackingNumber(), label.format(),
			label.labels());
	}

}

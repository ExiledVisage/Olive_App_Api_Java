package com.olivamore.api.notification;

import com.olivamore.api.shared.persistence.ValueEnum;

import java.util.Arrays;
import java.util.List;

/** Durtulebilecek isler: varsayilan sorumlu rol ve bildirim basligiyla. */
enum NudgeSubject implements ValueEnum {

	FINISHED_PRODUCT_RUN("finished_product_run", "warehouse_operator", "Dolum koşusu bekliyor"),
	ORDER("order", "warehouse_operator", "Sipariş hazırlanmayı bekliyor"),
	FINISHED_PRODUCT("finished_product", "warehouse_operator", "Ürün depoya konmayı bekliyor"),
	PRODUCTION_BATCH("production_batch", "production_operator", "Parti üretimde bekliyor"),
	BATCH_PROCESS("batch_process", "admin", "Aşama onay bekliyor");

	private final String value;
	private final String role;
	private final String title;

	NudgeSubject(String value, String role, String title) {
		this.value = value;
		this.role = role;
		this.title = title;
	}

	@Override
	public String value() {
		return value;
	}

	String role() {
		return role;
	}

	String title() {
		return title;
	}

	static List<String> roles() {
		return Arrays.stream(values()).map(NudgeSubject::role).distinct().toList();
	}

}

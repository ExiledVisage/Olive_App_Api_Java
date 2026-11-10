package com.olivamore.api.shared.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

import java.time.LocalDateTime;

/**
 * JSON kurallari. snake_case alan adlari {@code application.yaml}'da
 * ({@code spring.jackson.property-naming-strategy}). Burada:
 * <ul>
 *   <li>tarih-saatler ISO-8601, UTC offset'iyle ({@link UtcDateTimeSerializer})</li>
 *   <li>{@code BigDecimal} JSON sayisi, ustel gosterim olmadan</li>
 *   <li>metin alanina sayi ya da boolean, boolean alanina {@code true}/{@code false} disinda deger gelmez</li>
 *   <li>{@link ProblemDetail} RFC 9457 bicimiyle yazilir (filtrelerden yazilan cevaplar dahil)</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

	@Bean
	JacksonModule apiModule() {
		SimpleModule module = new SimpleModule("api")
			.addSerializer(LocalDateTime.class, new UtcDateTimeSerializer())
			.addDeserializer(Boolean.class, new StrictBooleanDeserializer())
			.addDeserializer(Boolean.TYPE, new StrictBooleanDeserializer());
		module.setMixInAnnotation(ProblemDetail.class, ProblemDetailJacksonMixin.class);
		return module;
	}

	@Bean
	JsonMapperBuilderCustomizer strictTypes() {
		return builder -> builder
			.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
			.withCoercionConfig(LogicalType.Textual, config -> config
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}

}

package com.olivamore.api.support;

import com.olivamore.api.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP uzerinden calisan entegrasyon testi: butun siniflar ayni Spring context'ini ve Postgres konteynerini
 * paylasir, her sinif demo verinin temiz haliyle baslar ({@link FreshDatabaseListener}). Context'i
 * degistiren ayar ({@code properties}, ek {@code @Import}) eklenmemeli; eklenirse sinif ayri context alir.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestExecutionListeners(listeners = FreshDatabaseListener.class,
	mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public @interface ApiIntegrationTest {
}

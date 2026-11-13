package com.olivamore.api.support;

import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.web.throttle.RateLimitBuckets;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.time.Clock;

/**
 * Her test sinifindan once veritabanini demo verinin ilk haline dondurur ({@link DemoSnapshot}: ilk sinifta demo
 * veri uretilir, sonrakilerde bellekteki kopyasi yuklenir). Boylece siniflar ayni Spring context'ini ve konteyneri
 * paylasir ama birbirinin verisini gormez. Bellekteki durum da sifirlanir: rate limit kovalari ve uygulama saati.
 */
public class FreshDatabaseListener extends AbstractTestExecutionListener {

	@Override
	public int getOrder() {
		return 1000;
	}

	@Override
	public void beforeTestClass(TestContext testContext) throws Exception {
		ApplicationContext context = testContext.getApplicationContext();
		DemoSnapshot.restore(context);
		context.getBeanProvider(RateLimitBuckets.class).ifAvailable(RateLimitBuckets::clear);
		Timestamps.useClock(Clock.systemUTC());
	}

}

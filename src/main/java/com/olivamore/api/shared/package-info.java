/**
 * Butun modullerin kullandigi ortak altyapi: ProblemDetail hata cevaplari, dogrulama mesajlari,
 * sayfalama, JSON ayarlari, zaman damgalari, istek limiti ve silme korumasi.
 * <p>
 * Is mantigi icermez. OPEN modul oldugu icin alt paketleri de diger
 * modullere aciktir.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")
package com.olivamore.api.shared;

import org.springframework.modulith.ApplicationModule;

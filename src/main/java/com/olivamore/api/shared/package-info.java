/**
 * Butun modullerin kullandigi ortak altyapi: Laravel uyumlu hata cevaplari,
 * sayfalama, JSON ayarlari, girdi normallestirme ve silme korumasi.
 * <p>
 * Is mantigi icermez. OPEN modul oldugu icin alt paketleri de diger
 * modullere aciktir.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")
package com.olivamore.api.shared;

import org.springframework.modulith.ApplicationModule;

/**
 * Mobil uygulamanin kendi sunucumuzdan guncellenmesi: {@code /api/app/version} ve
 * {@code /api/app/download}. Frontend'in yayinlama betigi APK'yi ve {@code version.json}'u
 * surum klasorune ({@code olive.releases-path}) koyar; uygulama acilista surume bakar,
 * daha yeniyse indirip kurar.
 * <p>
 * Veritabani kullanmaz, giris gerektirmez (guncelleme uyarisi giris ekraninda da cikabilmeli).
 */
@ApplicationModule(displayName = "App Release")
package com.olivamore.api.apprelease;

import org.springframework.modulith.ApplicationModule;

/**
 * Kimlik ve yetki: kullanicilar, roller, izinler (Spatie Laravel Permission tablolari), JWT access token'lar,
 * donen refresh token'lar, giris/kayit ve rate limit.
 * <p>
 * {@code /api/**} altindaki her endpoint giris ister (acik olanlar SecurityConfig'te). Diger moduller yetkiyi
 * controller'da {@code @PreAuthorize("hasAuthority('izin')")}, giris yapan kullaniciyi
 * {@code @AuthenticationPrincipal} {@link com.olivamore.api.identity.CurrentUser} ile alir.
 */
@ApplicationModule(displayName = "Identity")
package com.olivamore.api.identity;

import org.springframework.modulith.ApplicationModule;

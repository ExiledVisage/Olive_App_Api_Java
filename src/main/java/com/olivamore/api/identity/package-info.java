/**
 * Kimlik ve yetki: kullanicilar, roller, izinler (Spatie Laravel Permission tablolari),
 * Sanctum uyumlu token'lar, giris/kayit ve rate limit.
 * <p>
 * Diger modullerin kullandigi API bu pakettedir:
 * <ul>
 *   <li>{@link com.olivamore.api.identity.Public}: giris gerektirmeyen endpoint</li>
 *   <li>{@link com.olivamore.api.identity.RequiresRole} / {@link com.olivamore.api.identity.RequiresPermission}:
 *       Laravel'in {@code role:} / {@code permission:} middleware'leri</li>
 *   <li>{@link com.olivamore.api.identity.CurrentUser}: giris yapmis kullanici (controller parametresi)</li>
 * </ul>
 * {@code /api/**} altindaki her endpoint varsayilan olarak giris ister ({@code auth:sanctum}).
 */
@ApplicationModule(displayName = "Identity")
package com.olivamore.api.identity;

import org.springframework.modulith.ApplicationModule;

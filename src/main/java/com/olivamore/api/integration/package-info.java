/**
 * Site ve pazar yeri entegrasyonlari (docs/INTEGRATIONS.md). Alt paketler:
 * <ul>
 * <li>{@code provider}: platform listesi ve ortak istemci arayuzu; her platformun istemcisi kendi paketinde
 * ({@code provider.woocommerce}, sonra {@code provider.trendyol}, {@code provider.hepsiburada})</li>
 * <li>{@code connection}: baglantilar ve sifreli erisim bilgileri</li>
 * <li>{@code catalog}: bizim urun ile platformdaki urunun eslestirmesi</li>
 * <li>{@code order}: platform siparislerinin siparis moduline alinmasi</li>
 * <li>{@code stock}: stok gonderim kuyrugu</li>
 * <li>{@code sync}: zamanlayici ve senkron gecmisi</li>
 * </ul>
 * Diger modullerin tablolarini (urun, siparis, musteri) yalnizca okur ve yazdigi siparisleri sales'in kurallarina
 * uygun girer.
 */
@ApplicationModule(displayName = "Integration")
package com.olivamore.api.integration;

import org.springframework.modulith.ApplicationModule;

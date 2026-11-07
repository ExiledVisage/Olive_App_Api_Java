/**
 * Analitik ve gunluk ozet (Faz 11): randiman, tedarikci ve musteri ozetleri, rapor olusturucu (olcu x gruplama x
 * filtre x donem), kayitli raporlar ve acilis ekraninin gunluk ozeti.
 * <p>
 * Butun modullerin tablolarini yalnizca okur (kayitli raporlar haric); hicbir modul buna bagli degil. Sorgular
 * Laravel'deki Query Builder sorgularinin aynisi, {@link com.olivamore.api.shared.persistence.Db} ile.
 */
@ApplicationModule(displayName = "Analytics")
package com.olivamore.api.analytics;

import org.springframework.modulith.ApplicationModule;

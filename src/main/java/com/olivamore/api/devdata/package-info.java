/**
 * Gelistirme verisi: bos bir veritabanini demo fabrikayla doldurur.
 * <p>
 * Tanim verisi (kullanicilar, urunler, hatlar, tanklar, tarifler) {@code devdata/master-data.sql}'den yuklenir; bir
 * yillik akis (partiler, on dinlendirme ve Boşalt, dolum, depo, siparis) bugunun tarihine gore calisan uygulamanin
 * API'si uzerinden yurutulur, boylece veri her zaman guncel is kurallarina uyar.
 * <p>
 * Is mantigi icermez, hicbir modul buna bagli degildir; diger modullere yalnizca HTTP ve SQL ile erisir.
 */
@ApplicationModule(displayName = "Dev Data")
package com.olivamore.api.devdata;

import org.springframework.modulith.ApplicationModule;

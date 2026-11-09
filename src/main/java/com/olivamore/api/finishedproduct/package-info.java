/**
 * Bitmis urun: dolum tarifleri (icerik oranlari, attribute kurallari, versiyonlar), bitmis urunler ve ambalaj
 * bilesenleri, dolum kosulari, bitmis urun depolari ve raflari.
 * <p>
 * Tablolari JPA ile; ambalaj, stok, urun tipi ve kullanici bilgilerini yalnizca okur. Diger moduller tarif
 * cevaplarini ve raf islemlerini {@code RecipeResources} ve {@code ShelfOperations} uzerinden kullanir.
 */
@ApplicationModule(displayName = "Finished product")
package com.olivamore.api.finishedproduct;

import org.springframework.modulith.ApplicationModule;

/**
 * Bitmis urun (Faz 8): dolum tarifleri (icerik oranlari, attribute kurallari, versiyonlar), bitmis urunler ve
 * ambalaj bilesenleri, dolum kosulari, bitmis urun depolari ve raflari.
 * <p>
 * Diger moduller gibi Laravel tablolarina {@link com.olivamore.api.shared.persistence.Db} ile yazar; ambalaj
 * kalemlerini, raf ve siparis tablolarini yalnizca okur.
 */
@ApplicationModule(displayName = "Finished product")
package com.olivamore.api.finishedproduct;

import org.springframework.modulith.ApplicationModule;

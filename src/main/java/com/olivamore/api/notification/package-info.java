/**
 * Bildirim (Faz 10): kullaniciya dusen uygulama ici bildirimler ve "durtme" (bekleyen is icin yetkilinin isciyi
 * uyarmasi). Laravel'de push ve mail yok; kanal kolonu ({@code in_app}) sonradan eklenecek push icin duruyor.
 * <p>
 * Durtulen isin kaydini (dolum kosusu, siparis, bitmis urun, parti, proses) diger modullerin arayuzlerinden
 * yalnizca okur.
 */
@ApplicationModule(displayName = "Notification")
package com.olivamore.api.notification;

import org.springframework.modulith.ApplicationModule;

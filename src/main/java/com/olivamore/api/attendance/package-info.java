/**
 * Giris-cikis ve mesai (docs/ATTENDANCE.md): iscinin uygulamasindaki surekli degisen QR kapida bekci ya da kapi
 * tableti tarafindan okutulur; giris mi cikis mi son kayittan cikar. Haftalik calisma planina gore gunluk calisilan
 * sure, fazla mesai, eksik ve gec kalma hesaplanir. Kullanici adlari icin {@code users} tablosunu yalnizca okur.
 */
@ApplicationModule(displayName = "Attendance")
package com.olivamore.api.attendance;

import org.springframework.modulith.ApplicationModule;

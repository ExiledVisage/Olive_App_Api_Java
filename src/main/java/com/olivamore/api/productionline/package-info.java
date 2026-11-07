/**
 * Uretim hatlari (Faz 4): hatlar ve girdi/cikti urunleri, asamalar (sira mantigi) ve asama uzantilari.
 * <p>
 * Asamalarin proses tiplerini {@link com.olivamore.api.process.ProcessResources} uzerinden yukler.
 * Diger modullere acik kisim {@link com.olivamore.api.productionline.ProductionLineResources}: urun
 * kategorisi/tipi ekranlarindaki hatlar ve uretim partilerinin asama uzantilari.
 */
@ApplicationModule(displayName = "Production Line")
package com.olivamore.api.productionline;

import org.springframework.modulith.ApplicationModule;

/**
 * Uretim (Faz 6): partiler (giris/cikis urun tipleri, takvim), parti prosesleri ve alt tipleri (tartim,
 * donusum, depo hareketi, analiz), alan degerleri ve onay akisi.
 * <p>
 * Asama uzantilarini {@link com.olivamore.api.productionline.ProductionLineResources}, proses alanlarini
 * {@link com.olivamore.api.process.ProcessResources}, materyal resource'larini
 * {@link com.olivamore.api.inventory.MaterialResources} uzerinden kullanir.
 */
@ApplicationModule(displayName = "Production")
package com.olivamore.api.production;

import org.springframework.modulith.ApplicationModule;

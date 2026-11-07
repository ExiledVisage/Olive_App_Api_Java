/**
 * Uretim urunleri (Faz 4): urun kategorileri (production products), urun tipleri, global attribute
 * tanimlari ve kategori-attribute eslemeleri.
 * <p>
 * Kategori ve tip ekranlarindaki uretim hatlarini {@link com.olivamore.api.productionline.ProductionLineResources}
 * uzerinden yukler. Laravel tablolarina Eloquent'in davranisiyla yazmak icin JPA degil
 * {@link com.olivamore.api.shared.persistence.Db} kullanilir.
 */
@ApplicationModule(displayName = "Product")
package com.olivamore.api.product;

import org.springframework.modulith.ApplicationModule;

/**
 * Uretim altyapisi ve ana veriler (Faz 4): urun kategorileri, urun tipleri, attribute tanimlari,
 * proses tipleri ve alanlari, asama uzanti tipleri, uretim hatlari, asamalar, asama uzantilari.
 * <p>
 * Laravel tablolarina Eloquent'in davranisiyla yazmak ve iliskileri Laravel'in sorgulariyla ayni
 * sirada okumak icin JPA degil {@link com.olivamore.api.shared.persistence.Db} kullanilir.
 */
@ApplicationModule(displayName = "Catalog")
package com.olivamore.api.catalog;

import org.springframework.modulith.ApplicationModule;

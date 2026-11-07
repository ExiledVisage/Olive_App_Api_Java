/**
 * Ambalaj (Faz 7): urun tedarikcileri (sise, kapak, etiket getiren firmalar), ambalaj kalemleri, lotlar ve
 * lot hareketleri (mal girisi, sayim duzeltmesi, fire, iade).
 * <p>
 * Tek gercek kaynak lotlarin {@code quantity_remaining} toplami; {@code packaging_items.current_stock} her
 * degisiklikten sonra yeniden hesaplanan bir onbellek. Dolum kosusu tahsisi (FIFO) ve tuketimi Faz 8'de.
 */
@ApplicationModule(displayName = "Packaging")
package com.olivamore.api.packaging;

import org.springframework.modulith.ApplicationModule;

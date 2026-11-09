/**
 * Stok (Faz 5): tanklar (storage units), izinli urun/tipler, tank attribute kurallari, bakiyeler,
 * materyaller ve stok hareketleri.
 * <p>
 * Yazma tarafi JPA ile; okuma tarafinda {@code production} ayni islemde JDBC ile yazdigi icin
 * materyal cevaplari ve tank durumu her seferinde SQL ile okunur.
 */
@ApplicationModule(displayName = "Inventory")
package com.olivamore.api.inventory;

import org.springframework.modulith.ApplicationModule;

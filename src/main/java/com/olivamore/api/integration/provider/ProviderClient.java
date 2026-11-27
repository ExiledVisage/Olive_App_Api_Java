package com.olivamore.api.integration.provider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bir platform baglantisinin istemcisi. Her platform kendi paketinde uygular; ortak kod (eslestirme, siparis alma,
 * stok kuyrugu) yalniz bu arayuzu bilir.
 */
public interface ProviderClient {

	/** Erisim bilgileri ve adres calisiyor mu; calismiyorsa kullaniciya gosterilecek gerekce. */
	ConnectionCheck check();

	/** Platformdaki butun satilabilir urunler (varyantli urunde her varyant ayri). */
	List<ExternalProduct> products();

	/**
	 * Hazirlanmayi bekleyen (odenmis) siparisler; {@code changedAfter} verilirse yalniz o andan sonra degisenler.
	 *
	 * @param changedAfter UTC
	 */
	List<ExternalOrder> orders(LocalDateTime changedAfter);

	/**
	 * Stoklari toplu gonderir (platform stok yonetimini acar). Platform tek tek kalem hatasi dondurebilir; sonuc her
	 * kalem icin. Platforma hic ulasilamazsa {@link ProviderException}.
	 */
	List<StockUpdate.Result> pushStock(List<StockUpdate> updates);

	/**
	 * Siparisin hazirlanmaya basladigini bildirir; yalniz {@link Provider#reportsPreparing()} platformlarda cagrilir.
	 * Platform reddederse ya da ulasilamazsa {@link ProviderException}.
	 *
	 * @return platformun bu bildirimle verdigi kayit (Hepsiburada paket numarasi); yoksa bos
	 */
	default String reportPreparing(ExternalOrder order) {
		throw new UnsupportedOperationException("Bu platformda hazırlanıyor bildirimi yok.");
	}

	/**
	 * Kargo etiketi; yalniz {@link Provider#printsLabel()} platformlarda, hazirlaniyor bildirildikten sonra.
	 *
	 * @param platformRef hazirlaniyor bildiriminin dondurdugu kayit (Hepsiburada paket numarasi)
	 */
	default ShippingLabel label(ExternalOrder order, String platformRef) {
		throw new UnsupportedOperationException("Bu platformda kargo etiketi yok.");
	}

	/** Bizde iptal edilen siparisin kalemlerinin tedarik edilemedigini bildirir (Trendyol, "stok tukendi"). */
	default void reportUnsupplied(ExternalOrder order) {
		throw new UnsupportedOperationException("Bu platformda tedarik edilemedi bildirimi yok.");
	}

	/**
	 * Siparisin faturalandigini fatura numarasiyla bildirir; yalniz {@link Provider#reportsInvoice()} platformlarda,
	 * hazirlaniyor bildirildikten sonra cagrilir.
	 */
	default void reportInvoiced(ExternalOrder order, String invoiceNumber) {
		throw new UnsupportedOperationException("Bu platformda fatura bildirimi yok.");
	}

	/** Faturanin dosyasini (PDF) yukler; platformda zaten fatura varsa hata vermez. */
	default void sendInvoiceFile(ExternalOrder order, String invoiceNumber, LocalDate issueDate, byte[] pdf) {
		throw new UnsupportedOperationException("Bu platformda fatura dosyası yükleme yok.");
	}

	/** @param message basariliysa magaza/hesap ozeti, degilse hatanin gerekcesi */
	record ConnectionCheck(boolean ok, String message) {
	}

}

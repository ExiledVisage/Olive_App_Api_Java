package com.olivamore.api.integration.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Muhasebe programinin istemcisi (Parasut; docs/INVOICES.md): cari ve urun bulma/acma, satis faturasi, e-fatura /
 * e-arsiv resmilestirme. Program reddederse ya da ulasilamazsa {@link ProviderException}.
 */
public interface AccountingClient {

	/** @param taxNumber VKN (10) ya da TCKN (11); bos olabilir */
	record Contact(String name, boolean company, String taxNumber, String taxOffice, String email, String phone,
		String address, String district, String city) {
	}

	/** @param salePrice KDV haric liste fiyati */
	record Product(String name, String code, int vatRate, BigDecimal salePrice) {
	}

	/** @param unitPrice KDV haric birim fiyat */
	record Line(String productId, String description, int quantity, BigDecimal unitPrice, int vatRate) {
	}

	/** @param description bizdeki fatura numarasi */
	record SalesInvoice(String contactId, String description, LocalDate issueDate, String orderNo, LocalDate orderDate,
		Contact buyer, List<Line> lines) {
	}

	/** E-arsivde internet satisi bilgisi (site ve pazar yeri siparisi). */
	record InternetSale(String url, String paymentType, String paymentPlatform, LocalDate paymentDate) {
	}

	/** @param errors bittiyse ve hata varsa gerekceler */
	record Job(boolean finished, boolean failed, List<String> errors) {
	}

	/** @param number resmi fatura numarasi; program henuz vermediyse bos */
	record EDocument(String type, String id, String number) {
	}

	/** Vergi numarasi ya da e-postayla bulunan cari, yoksa yeni cari; id doner. */
	String contact(Contact contact);

	/** Koduyla (yoksa adiyla) bulunan urun, yoksa yeni urun; id doner. */
	String product(Product product);

	/** Satis faturasini olusturur; id doner. */
	String createSalesInvoice(SalesInvoice invoice);

	/** Vergi numarasi e-fatura mukellefiyse posta kutusu adresi. */
	Optional<String> eInvoiceAddress(String taxNumber);

	/** E-fatura olarak resmilestirme isini baslatir; is id'si doner. */
	String formalizeEInvoice(String salesInvoiceId, String address);

	/** E-arsiv olarak resmilestirme isini baslatir; is id'si doner. */
	String formalizeEArchive(String salesInvoiceId, InternetSale internetSale);

	Job job(String jobId);

	/** Faturanin resmi belgesi; resmilesmemisse bos. */
	Optional<EDocument> eDocument(String salesInvoiceId);

	/** Resmi belgenin PDF'i; program henuz hazirlamadiysa bos. */
	Optional<byte[]> pdf(EDocument document);

	/** Resmilesmemis satis faturasini iptal eder. */
	void cancelSalesInvoice(String salesInvoiceId);

}

package com.olivamore.api.shared.error;

/**
 * Kayit bulunamadi (404). Laravel'in route model binding / findOrFail
 * mesajini birebir uretir:
 * <pre>{"message": "No query results for model [App\\Models\\Order] 999999"}</pre>
 * Mesajda Laravel'in model sinif adi gecer, Java sinif adi degil.
 */
public class ModelNotFoundException extends RuntimeException {

	/**
	 * @param laravelModel Laravel model adi, ornegin {@code "Order"} ya da {@code "ProductionBatch"}
	 * @param id istekte gelen deger, oldugu gibi
	 */
	public ModelNotFoundException(String laravelModel, Object id) {
		super("No query results for model [App\\Models\\" + laravelModel + "] " + id);
	}

}

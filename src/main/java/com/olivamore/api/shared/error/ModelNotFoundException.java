package com.olivamore.api.shared.error;

/**
 * Kayit bulunamadi (404). Laravel'in route model binding / findOrFail
 * mesajini birebir uretir:
 * <pre>{"message": "No query results for model [App\\Models\\Order] 999999"}</pre>
 * Mesajda Laravel'in model sinif adi gecer, Java sinif adi degil.
 */
public class ModelNotFoundException extends RuntimeException {

	/**
	 * @param laravelModel Laravel model adi: kisa ad ({@code "Order"} -> {@code App\Models\Order})
	 *                     ya da ters bolu iceren tam ad ({@code "Spatie\\Permission\\Models\\Role"})
	 * @param id istekte gelen deger, oldugu gibi
	 */
	public ModelNotFoundException(String laravelModel, Object id) {
		super("No query results for model [" + laravelClass(laravelModel) + "] " + id);
	}

	public static String laravelClass(String laravelModel) {
		return laravelModel.contains("\\") ? laravelModel : "App\\Models\\" + laravelModel;
	}

}

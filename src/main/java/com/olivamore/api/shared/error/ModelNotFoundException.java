package com.olivamore.api.shared.error;

/** Kayit bulunamadi (404). Cevapta kaynak adi ve istekte gelen id yazilir: {@code Order 999999 not found.} */
public class ModelNotFoundException extends RuntimeException {

	private final String resource;
	private final transient Object id;

	/**
	 * @param resource kaynak adi, ornek {@code "Order"}
	 * @param id istekte gelen deger, oldugu gibi
	 */
	public ModelNotFoundException(String resource, Object id) {
		super(resource + " " + id + " not found.");
		this.resource = resource;
		this.id = id;
	}

	public String resource() {
		return resource;
	}

	public Object id() {
		return id;
	}

}

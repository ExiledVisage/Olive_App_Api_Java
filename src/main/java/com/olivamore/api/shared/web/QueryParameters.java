package com.olivamore.api.shared.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Query ve path parametreleri: metin kirpilir, bos deger verilmemis sayilir ({@code ?search=} filtre degildir).
 * JSON govdesi bundan etkilenmez.
 */
@ControllerAdvice
class QueryParameters {

	@InitBinder
	void trimStrings(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

}

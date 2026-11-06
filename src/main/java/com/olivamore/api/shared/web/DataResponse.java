package com.olivamore.api.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Tek bir JsonResource'un cevabi: {@code {"data": {...}}}.
 * Laravel yeni olusturulan bir modelin Resource'unu 201 ile dondurur ({@code wasRecentlyCreated}).
 */
public record DataResponse<T>(T data) {

	public static <T> DataResponse<T> of(T data) {
		return new DataResponse<>(data);
	}

	public static <T> ResponseEntity<DataResponse<T>> created(T data) {
		return ResponseEntity.status(HttpStatus.CREATED).body(new DataResponse<>(data));
	}

}

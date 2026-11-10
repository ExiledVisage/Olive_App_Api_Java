package com.olivamore.api.identity.user;

import java.time.LocalDateTime;

/** Yalnizca hesap alanlari; sifre hic yazilmaz. */
public record RawUserResponse(Long id, String name, String email, LocalDateTime emailVerifiedAt,
	LocalDateTime createdAt, LocalDateTime updatedAt) {
}

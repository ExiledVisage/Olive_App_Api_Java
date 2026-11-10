package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.user.UserResponse;

/** Giris ve kayit cevabi. {@code expiresIn}: access token'in saniye cinsinden omru. */
record TokenResponse(String message, String accessToken, String refreshToken, String tokenType, long expiresIn,
	UserResponse user) {
}

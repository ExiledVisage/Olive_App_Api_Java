package com.olivamore.api.identity.auth;

/** Yeni token cifti; eski refresh token artik gecersiz. */
record RefreshResponse(String message, String accessToken, String refreshToken, String tokenType, long expiresIn) {
}

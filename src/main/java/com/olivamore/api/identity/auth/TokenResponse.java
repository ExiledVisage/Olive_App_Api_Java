package com.olivamore.api.identity.auth;

import com.olivamore.api.identity.user.UserResponse;

record TokenResponse(String message, String token, UserResponse user) {
}

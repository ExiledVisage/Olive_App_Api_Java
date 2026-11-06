package com.olivamore.api.identity;

/**
 * Giris yapmis kullanici (Laravel'de {@code $request->user()}). Controller parametresi olarak alinir:
 * <pre>
 * &#64;PostMapping("/api/orders")
 * OrderResource store(CurrentUser user, RequestInput input)
 * </pre>
 * {@link Public} endpoint'lerde null'dir.
 *
 * @param id      users.id
 * @param tokenId istegi dogrulayan personal_access_tokens.id
 */
public record CurrentUser(long id, String name, String email, long tokenId) {
}

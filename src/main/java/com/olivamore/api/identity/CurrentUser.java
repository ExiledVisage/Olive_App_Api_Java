package com.olivamore.api.identity;

/**
 * Giris yapmis kullanici; controller parametresi olarak alinir. {@link Public} endpoint'lerde null'dir.
 *
 * @param id      users.id
 * @param tokenId istegi dogrulayan personal_access_tokens.id
 */
public record CurrentUser(long id, String name, String email, long tokenId) {
}

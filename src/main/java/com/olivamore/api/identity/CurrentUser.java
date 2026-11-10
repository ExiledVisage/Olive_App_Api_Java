package com.olivamore.api.identity;

import java.util.UUID;

/**
 * Giris yapmis kullanici; controller parametresi olarak {@code @AuthenticationPrincipal} ile alinir.
 *
 * @param id        users.id
 * @param sessionId istegi dogrulayan oturum
 */
public record CurrentUser(long id, String name, String email, UUID sessionId) {
}

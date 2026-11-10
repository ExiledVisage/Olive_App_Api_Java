package com.olivamore.api.identity.security;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.identity.auth.TokenService;
import com.olivamore.api.identity.user.User;
import com.olivamore.api.identity.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dogrulanmis JWT'den giris yapmis kullanici. Oturum, kullanici, rol ve izinler her istekte veritabanindan okunur:
 * cikis, kullanici silme ve yetki degisikligi token'in suresini beklemeden gecerli olur.
 */
class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String ROLE_PREFIX = "ROLE_";

	private final TokenService tokens;
	private final UserRepository users;
	private final Assignments assignments;

	AuthenticatedUserConverter(TokenService tokens, UserRepository users, Assignments assignments) {
		this.tokens = tokens;
		this.users = users;
		this.assignments = assignments;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		long userId;
		UUID sessionId;
		try {
			userId = Long.parseLong(jwt.getSubject());
			sessionId = UUID.fromString(jwt.getClaimAsString(TokenService.SESSION_CLAIM));
		}
		catch (IllegalArgumentException | NullPointerException malformed) {
			throw invalid();
		}
		if (!tokens.isActive(sessionId, userId)) {
			throw invalid();
		}
		User user = users.findById(userId).orElseThrow(AuthenticatedUserConverter::invalid);
		CurrentUser principal = new CurrentUser(user.getId(), user.getName(), user.getEmail(), sessionId);
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities(userId));
	}

	private List<GrantedAuthority> authorities(long userId) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		assignments.roleNames(userId).forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
		assignments.permissionNames(userId).forEach(name -> authorities.add(new SimpleGrantedAuthority(name)));
		return authorities;
	}

	private static InvalidBearerTokenException invalid() {
		return new InvalidBearerTokenException("Invalid session.");
	}

}

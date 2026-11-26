package com.olivamore.api.integration.connection;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.integration.provider.ProviderClient;
import com.olivamore.api.shared.web.DataResponse;
import com.olivamore.api.shared.web.ListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Site ve pazar yeri baglantilari (docs/INTEGRATIONS.md 0). */
@RestController
class ConnectionController {

	private final Connections connections;

	ConnectionController(Connections connections) {
		this.connections = connections;
	}

	/** Baglanti formu: platformlar ve istedikleri erisim bilgileri. */
	@GetMapping("/api/integrations/providers")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<Connections.ProviderInfo> providers() {
		return new ListResponse<>(connections.providers());
	}

	@GetMapping("/api/integrations/connections")
	@PreAuthorize("hasAuthority('integration.view')")
	ListResponse<Connections.View> index() {
		return new ListResponse<>(connections.all());
	}

	@GetMapping("/api/integrations/connections/{id}")
	@PreAuthorize("hasAuthority('integration.view')")
	DataResponse<Connections.View> show(@PathVariable long id) {
		return DataResponse.of(connections.get(id));
	}

	@PostMapping("/api/integrations/connections")
	@PreAuthorize("hasAuthority('integration.manage')")
	ResponseEntity<DataResponse<Connections.View>> store(@RequestBody Connections.CreateRequest request,
		@AuthenticationPrincipal CurrentUser user) {
		return DataResponse.created(connections.create(request, user.id()));
	}

	@PatchMapping("/api/integrations/connections/{id}")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<Connections.View> update(@PathVariable long id, @RequestBody Connections.UpdateRequest request) {
		return DataResponse.of(connections.update(id, request));
	}

	@DeleteMapping("/api/integrations/connections/{id}")
	@PreAuthorize("hasAuthority('integration.manage')")
	ResponseEntity<Void> destroy(@PathVariable long id) {
		connections.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** Baglantiyi dene: erisim bilgileri ve adres calisiyor mu. */
	@PostMapping("/api/integrations/connections/{id}/check")
	@PreAuthorize("hasAuthority('integration.manage')")
	DataResponse<ProviderClient.ConnectionCheck> check(@PathVariable long id) {
		return DataResponse.of(connections.check(id));
	}

}

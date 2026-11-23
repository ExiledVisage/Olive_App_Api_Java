package com.olivamore.api.machine;

import com.olivamore.api.support.Api;
import com.olivamore.api.support.ApiClient.Response;
import com.olivamore.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static com.olivamore.api.support.Api.data;
import static com.olivamore.api.support.Api.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Makine kaydi: tipler ve olcum alanlari, makineler ve parcalari. Seed'de dort tip (Kirici, Malaksor, Dekantor,
 * Separator) ve her tipten tek parcali bir makine var.
 */
@ApiIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MachineApiIT {

	private static final int MALAXER = 2;
	private static final int DECANTER = 3;

	@LocalServerPort
	int port;

	Api api;

	@BeforeAll
	void setUp() {
		api = Api.on(port);
	}

	@Test
	void seedHasTheFourPressingMachinesWithTheirFields() {
		JsonNode types = api.get("/api/machine-types").json().get("data");
		JsonNode machines = api.get("/api/machines").json().get("data");

		assertThat(types.valueStream().map(t -> t.get("key").asString()))
			.containsExactly("kirici", "malaksor", "dekantor", "separator");
		JsonNode malaxer = types.get(1);
		assertThat(malaxer.get("machine_count").asLong()).isPositive();
		assertThat(malaxer.get("fields").valueStream().map(f -> f.get("field_key").asString()))
			.containsExactly("sicaklik", "devir", "inert_gaz", "ceket_suyu_sicakligi", "hazne_dolulugu", "mikro_talk");
		JsonNode decanter = types.get(2).get("fields");
		assertThat(decanter.valueStream().map(f -> f.get("field_key").asString())).containsExactly("devir", "debi",
			"su_ilavesi", "diferansiyel_devir", "su_sicakligi", "yag_cikis_sicakligi", "faz_sayisi");
		assertThat(decanter.valueStream().filter(f -> f.get("scope").asString().equals("machine"))
			.map(f -> f.get("field_key").asString())).containsExactly("faz_sayisi");
		JsonNode seeded = machines.valueStream().filter(m -> m.get("code").asString().equals("MLK-01")).findFirst()
			.orElseThrow();
		assertThat(seeded.get("machine_type").get("key").asString()).isEqualTo("malaksor");
		assertThat(seeded.get("parts")).hasSize(1);
	}

	@Test
	void machineInfoFieldsAreValidatedAndStoredOnTheMachine() {
		Response outOfRange = api.post("/api/machines", Map.of("machine_type_id", DECANTER, "code", "DKN-T1",
			"name", "Deneme Dekantör", "attributes", Map.of("faz_sayisi", 4, "devir", 3000)));
		Response notInteger = api.post("/api/machines", Map.of("machine_type_id", DECANTER, "code", "DKN-T1",
			"name", "Deneme Dekantör", "attributes", Map.of("faz_sayisi", "iki")));
		JsonNode machine = data(api.post("/api/machines", Map.of("machine_type_id", DECANTER, "code", "DKN-T1",
			"name", "Deneme Dekantör", "attributes", Map.of("faz_sayisi", 3))));
		JsonNode updated = data(api.patch("/api/machines/" + machine.get("id").asLong(),
			Map.of("attributes", Map.of("faz_sayisi", 2))));

		assertThat(outOfRange.status()).isEqualTo(422);
		JsonNode errors = outOfRange.json().get("errors");
		assertThat(errors.has("attributes.faz_sayisi")).isTrue();
		assertThat(errors.get("attributes.devir").get(0).asString())
			.isEqualTo("Bu makine tipinde böyle bir makine bilgisi alanı yok.");
		assertThat(notInteger.status()).isEqualTo(422);
		assertThat(machine.get("attributes").get("faz_sayisi").asInt()).isEqualTo(3);
		assertThat(updated.get("attributes").get("faz_sayisi").asInt()).isEqualTo(2);
	}

	@Test
	void aFieldCanBeRemovedFromAType() {
		long type = id(api.post("/api/machine-types", Map.of("key", "filtre", "name", "Filtre",
			"fields", List.of(Map.of("field_key", "basinc", "label", "Basınç", "data_type", "decimal", "unit", "bar")))));
		long field = data(api.get("/api/machine-types/" + type)).get("fields").get(0).get("id").asLong();

		assertThat(api.delete("/api/machine-types/" + type + "/fields/" + field).status()).isEqualTo(200);
		assertThat(data(api.get("/api/machine-types/" + type)).get("fields")).isEmpty();
	}

	@Test
	void typeKeysAndFieldRangesAreValidated() {
		Response duplicate = api.post("/api/machine-types", Map.of("key", "malaksor", "name", "Tekrar"));
		Response badKey = api.post("/api/machine-types", Map.of("key", "Filtre Pres", "name", "Filtre"));
		Response badRange = api.post("/api/machine-types", Map.of("key", "filtre_pres", "name", "Filtre Presi",
			"fields", List.of(
				Map.of("field_key", "basinc", "label", "Basınç", "data_type", "decimal", "min_value", 8, "max_value", 2),
				Map.of("field_key", "not", "label", "Not", "data_type", "text", "min_value", 1),
				Map.of("field_key", "not", "label", "Not 2", "data_type", "text"))));

		assertThat(duplicate.status()).isEqualTo(422);
		assertThat(duplicate.json().get("errors").has("key")).isTrue();
		assertThat(badKey.status()).isEqualTo(422);
		assertThat(badRange.status()).isEqualTo(422);
		JsonNode errors = badRange.json().get("errors");
		assertThat(errors.has("fields.0.max_value")).isTrue();
		assertThat(errors.get("fields.1.min_value").get(0).asString())
			.isEqualTo("En küçük ve en büyük değer yalnızca sayısal alanda verilir.");
		assertThat(errors.has("fields.2.field_key")).isTrue();
	}

	@Test
	void aMachineWithoutPartsGetsASinglePartAndKeepsAtLeastOne() {
		JsonNode machine = data(api.post("/api/machines", Map.of("machine_type_id", MALAXER, "code", "MLK-T1",
			"name", "Deneme Malaksör")));
		long id = machine.get("id").asLong();
		long only = machine.get("parts").get(0).get("id").asLong();

		assertThat(machine.get("parts")).hasSize(1);
		assertThat(machine.get("parts").get(0).get("name").asString()).isEqualTo("Deneme Malaksör");
		Response lastPart = api.delete("/api/machines/" + id + "/parts/" + only);
		assertThat(lastPart.status()).isEqualTo(422);
		assertThat(lastPart.json().get("detail").asString()).isEqualTo("'Deneme Malaksör' makinesinin tek parçası "
			+ "silinemez; her makinede en az bir parça olmalı.");

		long second = id(api.post("/api/machines/" + id + "/parts", Map.of("code", "2", "name", "Hazne 2")));
		Response duplicateCode = api.post("/api/machines/" + id + "/parts", Map.of("code", "2", "name", "Hazne 2b"));
		assertThat(duplicateCode.status()).isEqualTo(422);
		assertThat(api.delete("/api/machines/" + id + "/parts/" + only).status()).isEqualTo(200);
		assertThat(data(api.get("/api/machines/" + id)).get("parts").get(0).get("id").asLong()).isEqualTo(second);
	}

	@Test
	void aTypeWithMachinesCannotBeDeletedButCanBeDeactivated() {
		long type = id(api.post("/api/machine-types", Map.of("key", "yikama", "name", "Yıkama",
			"fields", List.of(Map.of("field_key", "su_degisimi", "label", "Su Değişimi", "data_type", "boolean")))));
		api.post("/api/machines", Map.of("machine_type_id", type, "code", "YKM-01", "name", "Yıkama 1",
			"parts", List.of(Map.of("code", "1", "name", "Hat 1"), Map.of("code", "2", "name", "Hat 2"))));

		Response delete = api.delete("/api/machine-types/" + type);
		Response deactivate = api.patch("/api/machine-types/" + type, Map.of("is_active", false));
		Response machineOfPassiveType = api.post("/api/machines", Map.of("machine_type_id", type, "code", "YKM-02",
			"name", "Yıkama 2"));

		assertThat(delete.status()).isEqualTo(422);
		assertThat(delete.json().get("detail").asString())
			.isEqualTo("'Yıkama' makine tipi silinemez; kullanıldığı yerler: 1 makine. Pasife alabilirsiniz.");
		assertThat(data(deactivate).get("is_active").asBoolean()).isFalse();
		assertThat(machineOfPassiveType.status()).isEqualTo(422);
		assertThat(data(api.get("/api/machines?machine_type_id=" + type)).get(0).get("parts")).hasSize(2);
	}

	@Test
	void onlyMachineManagersChangeMachines() {
		Response planner = api.as("planner@olivamore.com").get("/api/machines");
		Response plannerCreate = api.as("planner@olivamore.com").post("/api/machines",
			Map.of("machine_type_id", MALAXER, "code", "MLK-X", "name", "X"));
		Response warehouse = api.as("depo@olivamore.com").get("/api/machine-types");

		assertThat(planner.status()).isEqualTo(200);
		assertThat(plannerCreate.status()).isEqualTo(403);
		assertThat(warehouse.status()).isEqualTo(403);
	}

}

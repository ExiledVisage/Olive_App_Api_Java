package com.olivamore.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Modul sinirlarini korur: bir modul baska bir modulun ic paketlerine erisirse
 * ya da moduller arasinda dongu olusursa bu test kirilir.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(OliveApiApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		// target/spring-modulith-docs altina modul diyagramlari uretir
		new Documenter(modules).writeDocumentation();
	}

}

package com.olivamore.api;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Bolum B'nin kabul kriteri: uygulama kodunda Laravel'den tasinmis yardimci yapilar kalmaz. Yalnizca uygulama
 * kodu taranir, testler taranmaz.
 */
@AnalyzeClasses(packagesOf = OliveApiApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class NoLaravelLeftoversTests {

	private static final Set<String> FORBIDDEN = Set.of("Php", "Db", "Row", "RequestInput", "LaravelValidator");

	@ArchTest
	static final ArchRule noLaravelStructures = classes().should(new ArchCondition<>("not be a Laravel leftover") {
		@Override
		public void check(JavaClass javaClass, ConditionEvents events) {
			String name = javaClass.getSimpleName();
			if (FORBIDDEN.contains(name) || name.contains("Laravel")) {
				events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " Laravel'den kalan bir yapi"));
			}
		}
	});

}

package org.samlier.runner.cases;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.runner.cases.MetadataFixtureObservationTestCase.Behavior;
import org.samlier.runner.cases.MetadataFixtureObservationTestCase.Fixture;

/** Product-neutral metadata fixture implementations for approved M2 CONFIG cases. */
final class MetadataConfigCaseFactory {
    private static final Map<String, List<Fixture>> FIXTURES = Map.ofEntries(
            Map.entry("IIP-MD02.b", List.of(
                    accept("redirect-301", "follow HTTP 301"),
                    accept("redirect-302", "follow HTTP 302"),
                    accept("redirect-307", "follow HTTP 307"))),
            Map.entry("IIP-MD02.c", List.of(
                    accept("entity-root", "consume an EntityDescriptor root"),
                    accept("entities-root-one", "consume an EntitiesDescriptor root"))),
            Map.entry("IIP-MD02.d", List.of(
                    accept("entities-root-one", "consume one child"),
                    accept("entities-root-two", "find the tested entity after another child"),
                    accept("entities-root-fifty", "find the tested entity after forty-nine children"))),
            Map.entry("IIP-MD04.a", List.of(
                    reject("no-valid-until", "reject a root without validUntil in the enabled policy state"))),
            Map.entry("IIP-MD04.b", List.of(
                    reject("expired", "reject a root whose validUntil is in the past"))),
            Map.entry("IIP-MD05.a4", List.of(
                    accept("entity-root", "consume the single-entity root"),
                    accept("entities-root-one", "consume the multiple-entity root"))),
            Map.entry("IIP-MD05.a5", List.of(
                    accept("entity-cache-duration", "consume EntityDescriptor with cacheDuration only"),
                    accept("entities-cache-duration", "consume EntitiesDescriptor with cacheDuration only"),
                    accept("entity-root", "consume EntityDescriptor with validUntil"),
                    accept("entities-valid-until", "consume EntitiesDescriptor with validUntil"))),
            Map.entry("IIP-MD05.g", List.of(
                    accept("unknown-extension", "ignore a well-formed unknown extension without failure"),
                    accept("mdrpi-registration-info", "consume a real non-mandatory metadata extension"))),
            Map.entry("IIP-MD06.a1", List.of(
                    accept("entity-root", "resolve the tested entity from an EntityDescriptor root"),
                    accept("entities-root-one", "resolve it from an EntitiesDescriptor root"),
                    accept("nested-entities", "resolve it through nested EntitiesDescriptor elements"))));

    private MetadataConfigCaseFactory() {}

    static Optional<org.samlier.core.caseexec.TestCase> create(CaseDefinition definition) {
        var fixtures = FIXTURES.get(definition.obligation());
        return fixtures == null ? Optional.empty() : Optional.of(new MetadataFixtureObservationTestCase(
                definition.id(), definition.role(), fixtures, definition.configurationFailureSemantics()));
    }

    private static Fixture accept(String variant, String purpose) {
        return new Fixture(variant, Behavior.ACCEPT, purpose);
    }

    private static Fixture reject(String variant, String purpose) {
        return new Fixture(variant, Behavior.REJECT, purpose);
    }
}

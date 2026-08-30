package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.casedef.CaseDefinitionCatalog.Requirements;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.plan.TargetRole;

class MetadataConfigCaseFactoryTest {
    @Test
    void duplicateAndExtensionObligationsUseProtocolEvidenceInsteadOfVerdictForms() {
        for (var obligation : List.of(
                "IIP-MD03.a", "IIP-MD03.b", "IIP-MD03.c",
                "IIP-MD05.a1", "IIP-MD05.a2", "IIP-MD05.a3", "IIP-MD05.as", "IIP-MD05.cd",
                "IIP-MD12.a", "IIP-MD12.b", "IIP-MD12.c", "IIP-MD12.d")) {
            assertInstanceOf(MetadataFixtureObservationTestCase.class,
                    MetadataConfigCaseFactory.create(definition(obligation)).orElseThrow());
        }
    }

    @Test
    void unrelatedConfigurationCasesStillUseTheApprovedFallback() {
        assertTrue(MetadataConfigCaseFactory.create(definition("IIP-IDP09.a")).isEmpty());
    }

    private CaseDefinition definition(String obligation) {
        return new CaseDefinition(
                obligation + "-idp-01", obligation, TargetRole.IDP, ExecutionMode.CONFIG, Milestone.M2,
                List.of(), Map.of(), List.of(), List.of(), List.of(), "counterexample",
                List.of(), new Requirements(List.of(), "none"), false,
                ConfigurationFailureSemantics.TEST_PRECONDITION,
                "sha256:" + "0".repeat(64));
    }
}

package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.samlier.core.casedef.CaseDefinitionCatalogMapper;
import org.samlier.core.evaluation.CoverageCatalogMapper;
import org.samlier.core.evaluation.PredicateCatalogMapper;
import org.samlier.runner.result.EvaluationArtifactDigests;
import org.samlier.runner.CaseImplementationAudit;
import org.samlier.runner.cases.ApprovedAttestedCaseRegistry;
import org.samlier.runner.cases.ApprovedConfigCaseRegistry;
import org.samlier.runner.cases.ApprovedBrowserCaseRegistry;
import org.samlier.runner.cases.M2AutomatedCaseRegistry;
import org.samlier.runner.cases.M3AutomatedCaseRegistry;
import org.samlier.runner.cases.AttestedOutcomeTestCase;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;

class CatalogDocumentsTest {
    @Test
    void embedsAndStrictlyMapsTheSignedG1AndG2Catalogs() throws Exception {
        var documents = CatalogDocuments.load();
        var coverage = CoverageCatalogMapper.fromDocument(documents.parsed("tests/coverage.yaml"));
        var predicates = PredicateCatalogMapper.fromDocument(documents.parsed("tests/predicates.yaml"));
        var cases = CaseDefinitionCatalogMapper.fromDocument(documents.parsed("tests/cases.yaml"));
        var digests = EvaluationArtifactDigests.fromDocuments(
                documents.bytes("tests/coverage.yaml"), documents.testDefinitions(),
                documents.bytes("tests/specs.yaml"));

        assertEquals(544, coverage.obligations().size());
        assertEquals(26, predicates.definitions().size());
        assertEquals(732, cases.cases().size());
        var attested = ApprovedAttestedCaseRegistry.create(cases);
        assertEquals(47, attested.ids().size());
        CaseImplementationAudit.requireExact(cases, attested, Milestone.M1, ExecutionMode.ATTESTED);
        var firstAttestation = (AttestedOutcomeTestCase) attested.require("IIP-G02-c-idp-01");
        assertEquals(java.util.List.of("satisfied", "violated", "unable_to_verify"),
                firstAttestation.options().stream().map(value -> value.value()).toList());
        assertTrue(firstAttestation.promptEn().contains("SPProvidedID"));
        assertTrue(firstAttestation.promptEn().contains("Counterexample to avoid"));
        var config = ApprovedConfigCaseRegistry.create(cases);
        assertEquals(65, config.ids().size());
        CaseImplementationAudit.requireExact(cases, config, Milestone.M1, ExecutionMode.CONFIG);
        var browser = ApprovedBrowserCaseRegistry.create(cases, java.net.URI.create("https://suite.example"));
        assertEquals(151, browser.ids().size());
        CaseImplementationAudit.requireExact(cases, browser, Milestone.M1, ExecutionMode.BROWSER);
        assertEquals(12, ApprovedAttestedCaseRegistry.create(cases, Milestone.M2).ids().size());
        assertEquals(214, ApprovedConfigCaseRegistry.create(cases, Milestone.M2).ids().size());
        assertEquals(20, ApprovedBrowserCaseRegistry.create(
                cases, java.net.URI.create("https://suite.example"), Milestone.M2).ids().size());
        var m2Automated = M2AutomatedCaseRegistry.create(runId -> null);
        assertEquals(18, m2Automated.ids().size());
        CaseImplementationAudit.requireExact(cases, m2Automated, Milestone.M2, ExecutionMode.AUTOMATED);
        assertEquals(11, ApprovedAttestedCaseRegistry.create(cases, Milestone.M3).ids().size());
        assertEquals(9, ApprovedConfigCaseRegistry.create(cases, Milestone.M3).ids().size());
        assertEquals(83, ApprovedBrowserCaseRegistry.create(
                cases, java.net.URI.create("https://suite.example"), Milestone.M3).ids().size());
        var m3Automated = M3AutomatedCaseRegistry.create(runId -> null, entry -> new byte[0], runId -> java.util.List.of());
        assertEquals(35, m3Automated.ids().size());
        CaseImplementationAudit.requireExact(cases, m3Automated, Milestone.M3, ExecutionMode.AUTOMATED);
        assertFalse(digests.compositeDigest().isBlank());
        assertArrayEquals(Files.readAllBytes(repositoryRoot().resolve("tests/coverage.yaml")),
                documents.bytes("tests/coverage.yaml"));
    }

    private Path repositoryRoot() {
        for (var value : java.util.List.of(Path.of("."), Path.of(".."))) {
            var root = value.toAbsolutePath().normalize();
            if (Files.isRegularFile(root.resolve("tests/coverage.yaml"))) return root;
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}

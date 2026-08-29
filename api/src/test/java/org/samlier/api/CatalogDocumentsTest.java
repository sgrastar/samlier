package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.samlier.core.casedef.CaseDefinitionCatalogMapper;
import org.samlier.core.evaluation.CoverageCatalogMapper;
import org.samlier.core.evaluation.PredicateCatalogMapper;
import org.samlier.runner.result.EvaluationArtifactDigests;

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

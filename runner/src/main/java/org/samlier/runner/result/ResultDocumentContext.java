package org.samlier.runner.result;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Immutable publication metadata that is not itself a determination. */
public record ResultDocumentContext(
        Suite suite,
        EvaluationComponents evaluationComponents,
        ProfileSpec profileSpec,
        TargetDeclaration target,
        Map<String, String> requirementSpecUrls,
        Map<String, String> caseDefinitionUrls,
        List<ResultDocument.AdvisoryView> advisories) {

    public ResultDocumentContext {
        java.util.Objects.requireNonNull(suite, "suite");
        java.util.Objects.requireNonNull(evaluationComponents, "evaluationComponents");
        java.util.Objects.requireNonNull(profileSpec, "profileSpec");
        java.util.Objects.requireNonNull(target, "target");
        requirementSpecUrls = Map.copyOf(requirementSpecUrls == null ? Map.of() : requirementSpecUrls);
        caseDefinitionUrls = Map.copyOf(caseDefinitionUrls == null ? Map.of() : caseDefinitionUrls);
        advisories = List.copyOf(advisories == null ? List.of() : advisories);
    }

    public record Suite(String name, String version, String imageDigest, String executionMode) {
        public Suite {
            text(name, "suite.name");
            text(version, "suite.version");
            text(imageDigest, "suite.imageDigest");
            text(executionMode, "suite.executionMode");
        }
    }

    public record EvaluationComponents(
            String coverageYaml,
            String testDefinitions,
            String specsYaml,
            String outcomeMappingVersion,
            String aggregationPolicyVersion) {
        public EvaluationComponents {
            digest(coverageYaml, "coverageYaml");
            digest(testDefinitions, "testDefinitions");
            digest(specsYaml, "specsYaml");
            text(outcomeMappingVersion, "outcomeMappingVersion");
            text(aggregationPolicyVersion, "aggregationPolicyVersion");
        }

        public String compositeDigest() {
            var canonical = "coverage_yaml=" + coverageYaml + "\n"
                    + "test_definitions=" + testDefinitions + "\n"
                    + "specs_yaml=" + specsYaml + "\n"
                    + "outcome_mapping_version=" + outcomeMappingVersion + "\n"
                    + "aggregation_policy_version=" + aggregationPolicyVersion + "\n";
            try {
                return "sha256:" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }
    }

    public record ProfileSpec(String document, String version, LocalDate date, String levelDefinitionNote) {
        public ProfileSpec {
            text(document, "profileSpec.document");
            text(version, "profileSpec.version");
            java.util.Objects.requireNonNull(date, "profileSpec.date");
            text(levelDefinitionNote, "profileSpec.levelDefinitionNote");
        }
    }

    public record TargetDeclaration(
            String product,
            String declaredBy,
            String metadataDigest) {
        public TargetDeclaration {
            text(product, "target.product");
            text(declaredBy, "target.declaredBy");
            digest(metadataDigest, "target.metadataDigest");
        }
    }

    private static void digest(String value, String name) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

package org.samlier.runner.result;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.evaluation.CaseRun;
import org.samlier.core.evaluation.RunResult;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

/** Builds truthful publication metadata from fixed Suite inputs and the exact evaluated snapshot. */
public final class DefaultResultContextProvider implements ResultContextProvider {
    private final ResultDocumentContext.Suite suite;
    private final ResultDocumentContext.EvaluationComponents components;
    private final URI requirementCatalog;
    private final URI caseCatalog;
    private final Function<TestRun, byte[]> targetMetadata;

    public DefaultResultContextProvider(
            ResultDocumentContext.Suite suite,
            ResultDocumentContext.EvaluationComponents components,
            URI requirementCatalog,
            URI caseCatalog,
            Function<TestRun, byte[]> targetMetadata) {
        this.suite = Objects.requireNonNull(suite, "suite");
        this.components = Objects.requireNonNull(components, "components");
        this.requirementCatalog = absolute(requirementCatalog, "requirementCatalog");
        this.caseCatalog = absolute(caseCatalog, "caseCatalog");
        this.targetMetadata = Objects.requireNonNull(targetMetadata, "targetMetadata");
    }

    @Override
    public ResultDocumentContext context(
            TestRun run, TestPlan plan, List<CaseRun> cases, RunResult evaluation) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(plan, "plan");
        cases = List.copyOf(cases);
        Objects.requireNonNull(evaluation, "evaluation");
        var requirementUrls = new LinkedHashMap<String, String>();
        evaluation.requirements().forEach(value ->
                requirementUrls.put(value.id(), fragment(requirementCatalog, value.id())));
        var caseUrls = new LinkedHashMap<String, String>();
        cases.forEach(value -> caseUrls.put(value.id(), fragment(caseCatalog, value.id())));
        var metadata = targetMetadata.apply(run);
        if (metadata == null || metadata.length == 0) {
            throw new IllegalStateException("Target metadata is unavailable for public result provenance");
        }
        return new ResultDocumentContext(
                suite, components,
                new ResultDocumentContext.ProfileSpec(
                        "SAML V2.0 Implementation Profile for Federation Interoperability",
                        "1.1", LocalDate.parse("2019-12-18"),
                        "Core and Full are Samlier test scopes; the RFC 2119 level of each obligation remains authoritative."),
                new ResultDocumentContext.TargetDeclaration(
                        plan.name(), "Test Plan operator", EvaluationArtifactDigests.digestBytes(metadata)),
                requirementUrls, caseUrls, List.of());
    }

    private static URI absolute(URI value, String name) {
        if (value == null || !value.isAbsolute() || value.getHost() == null) {
            throw new IllegalArgumentException(name + " must be an absolute URL");
        }
        return value;
    }

    private static String fragment(URI base, String value) {
        return URI.create(base.toString().split("#", 2)[0] + "#" + value).toString();
    }
}

package org.samlier.runner.cases;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;

public final class DiscoveryMetadataTestCase implements TestCase {
    private static final Map<String, DiscoveryMetadataProfileCase.Rule> RULES = Map.of(
            "IIP-SP04-h-sp-01", DiscoveryMetadataProfileCase.Rule.FIXED_BINDING,
            "IIP-SP04-i-sp-01", DiscoveryMetadataProfileCase.Rule.INDEXED_ENDPOINT_STRUCTURE);
    private final String id;
    private final Function<String, byte[]> metadata;

    public DiscoveryMetadataTestCase(String id, Function<String, byte[]> metadata) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved Discovery case: " + id);
        this.id = id;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }
    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.SP; }
    @Override public CaseStep start(CaseContext context) {
        return new CaseStep.Finish(new DiscoveryMetadataProfileCase(RULES.get(id)).evaluate(
                metadata.apply(context.runId())));
    }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Discovery metadata cases finish during start");
    }
    static java.util.Set<String> approvedIds() { return RULES.keySet(); }
}

package com.samlscope.runner.cases;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;

/** Executable wrapper for one approved role-specific metadata-signature rule. */
public final class MetadataSignatureTestCase implements TestCase {
    private static final Map<String, MetadataSignatureProfileCase.Rule> RULES = rules();
    private final String id;
    private final Function<String, byte[]> metadata;

    public MetadataSignatureTestCase(String id, Function<String, byte[]> metadata) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved metadata signature case: " + id);
        this.id = id;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }
    @Override public CaseStep start(CaseContext context) {
        return new CaseStep.Finish(new MetadataSignatureProfileCase(RULES.get(id)).evaluate(
                metadata.apply(context.runId())));
    }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Metadata signature cases finish during start");
    }

    public static java.util.Set<String> approvedIds() { return RULES.keySet(); }

    private static Map<String, MetadataSignatureProfileCase.Rule> rules() {
        var result = new java.util.LinkedHashMap<String, MetadataSignatureProfileCase.Rule>();
        bind(result, "ag", MetadataSignatureProfileCase.Rule.ENVELOPED);
        bind(result, "ah", MetadataSignatureProfileCase.Rule.RSA_SHA1);
        bind(result, "ai", MetadataSignatureProfileCase.Rule.SIGNED_ELEMENT_ID);
        bind(result, "aj", MetadataSignatureProfileCase.Rule.SINGLE_ROOT_REFERENCE);
        bind(result, "ak", MetadataSignatureProfileCase.Rule.EXCLUSIVE_C14N);
        bind(result, "al", MetadataSignatureProfileCase.Rule.ALLOWED_TRANSFORMS);
        return Map.copyOf(result);
    }

    private static void bind(
            Map<String, MetadataSignatureProfileCase.Rule> result,
            String suffix,
            MetadataSignatureProfileCase.Rule rule) {
        result.put("IIP-MD05-" + suffix + "-idp-01", rule);
        result.put("IIP-MD05-" + suffix + "-sp-01", rule);
    }
}

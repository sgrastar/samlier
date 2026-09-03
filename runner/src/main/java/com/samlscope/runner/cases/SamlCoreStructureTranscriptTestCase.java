package com.samlscope.runner.cases;

import java.util.Map;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.plan.TargetRole;

/** Executable wrappers for eight approved IdP-side SAML Core structural cases. */
public final class SamlCoreStructureTranscriptTestCase implements TestCase {
    private static final Map<String, SamlCoreStructureCase.Rule> RULES = Map.of(
            "IIP-SSO01-ch-idp-01", SamlCoreStructureCase.Rule.TOP_LEVEL_STATUS_CODE,
            "IIP-SSO01-ci-idp-01", SamlCoreStructureCase.Rule.GENERIC_STATEMENT_TYPE,
            "IIP-SSO01-cj-idp-01", SamlCoreStructureCase.Rule.SUBJECT_WITHOUT_STATEMENTS,
            "IIP-SSO01-ck-idp-01", SamlCoreStructureCase.Rule.GENERIC_CONDITION_TYPE,
            "IIP-SSO01-cl-idp-01", SamlCoreStructureCase.Rule.ONE_TIME_USE_LIMIT,
            "IIP-SSO01-cm-idp-01", SamlCoreStructureCase.Rule.PROXY_RESTRICTION_LIMIT,
            "IIP-SSO01-dd-idp-01", SamlCoreStructureCase.Rule.SUBJECT_FOR_AUTHN_STATEMENT,
            "IIP-SSO01-dh-idp-01", SamlCoreStructureCase.Rule.SUBJECT_FOR_ATTRIBUTE_STATEMENT);

    private final String id;
    private final TranscriptContentReader content;

    public SamlCoreStructureTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved structure case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override
    public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) {
            throw new IllegalStateException("Passive structure cases must run against the complete Transcript");
        }
        var oracle = new SamlCoreStructureCase(RULES.get(id));
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive structure cases finish during start");
    }
}

package com.samlscope.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlCbcEncryptedAssertionSignatureTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-fv-idp-01";
    private final TranscriptContentReader content;
    private final SamlCbcEncryptedAssertionSignatureCase oracle;

    public SamlCbcEncryptedAssertionSignatureTranscriptTestCase(
            String id, TranscriptContentReader content, List<X509Certificate> targetSigningCertificates) {
        if (!CASE_ID.equals(id)) throw new IllegalArgumentException("Unapproved CBC signature case id: " + id);
        this.content = Objects.requireNonNull(content, "content");
        this.oracle = new SamlCbcEncryptedAssertionSignatureCase(
                targetSigningCertificates, new com.samlscope.saml.crypto.XmlSignatureVerifier());
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("CBC signature case requires a complete Transcript");
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("CBC signature case finishes during start");
    }
}

package com.samlscope.runner.cases;

import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class EcpTranscriptTestCase implements TestCase {
    private static final Map<String, EcpTranscriptProfileCase.Rule> RULES = rules();
    private final String id;
    private final TranscriptContentReader content;
    private final Function<String, List<X509Certificate>> certificates;
    private final SamlDecryptionKeyProvider decryptionKeys;

    public EcpTranscriptTestCase(
            String id,
            TranscriptContentReader content,
            Function<String, List<X509Certificate>> certificates,
            SamlDecryptionKeyProvider decryptionKeys) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved ECP case: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
        this.certificates = Objects.requireNonNull(certificates, "certificates");
        this.decryptionKeys = Objects.requireNonNull(decryptionKeys, "decryptionKeys");
    }
    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) {
        return new CaseStep.Finish(new EcpTranscriptProfileCase(
                RULES.get(id), certificates.apply(context.runId()),
                decryptionKeys.keyFor(context.runId()).orElse(null)).evaluate(
                context.runId(), context.transcript(), content));
    }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive ECP cases finish during start");
    }
    static java.util.Set<String> approvedIds() { return RULES.keySet(); }

    private static Map<String, EcpTranscriptProfileCase.Rule> rules() {
        var values = new LinkedHashMap<String, EcpTranscriptProfileCase.Rule>();
        values.put("IIP-IDP13-a-idp-01", EcpTranscriptProfileCase.Rule.BASIC_EXCHANGE);
        values.put("IIP-IDP13-e-idp-01", EcpTranscriptProfileCase.Rule.RESPONSE_OR_FAULT);
        values.put("IIP-IDP13-g-idp-01", EcpTranscriptProfileCase.Rule.RESPONSE_HEADER);
        values.put("IIP-IDP13-h-idp-01", EcpTranscriptProfileCase.Rule.REQUEST_AUTHENTICATED);
        values.put("IIP-IDP13-i-idp-01", EcpTranscriptProfileCase.Rule.HEADER_ATTRIBUTES);
        values.put("IIP-IDP13-j-idp-01", EcpTranscriptProfileCase.Rule.RESPONSE_INTEGRITY);
        values.put("IIP-IDP13-o-idp-01", EcpTranscriptProfileCase.Rule.RELAY_STATE_CHOICE);
        values.put("IIP-IDP13-p-idp-01", EcpTranscriptProfileCase.Rule.DELEGATION_CHOICE);
        values.put("IIP-IDP13-r-idp-01", EcpTranscriptProfileCase.Rule.INTERMEDIATE_EXCHANGES);
        values.put("IIP-IDP13-c-idp-01", EcpTranscriptProfileCase.Rule.BEARER_CONFIRMATION);
        values.put("IIP-IDP13-d-idp-01", EcpTranscriptProfileCase.Rule.CHANNEL_BINDINGS);
        values.put("IIP-IDP14-a-idp-01", EcpTranscriptProfileCase.Rule.HTTP_BASIC);
        values.put("IIP-IDP15-a-idp-01", EcpTranscriptProfileCase.Rule.GENERATED_KEY);
        return Map.copyOf(values);
    }
}

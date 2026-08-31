package org.samlier.runner.cases;

import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.runner.EvidenceCampaignCase;
import org.samlier.runner.RunCampaignQuery;

/**
 * Keeps an approved browser case's outcome behavior while declaring a product-neutral policy
 * operation that can prepare several cases together. Completing the operation never supplies a
 * target outcome: the wrapped legacy browser case still finishes NOT_VERIFIED when no external
 * oracle is available.
 */
public final class SharedBrowserPolicyTestCase
        implements TestCase, BrowserPrompt, EvidenceCampaignCase, org.samlier.runner.OperatorAssistedCase {
    private static final List<String> BLOCK_ENCRYPTION = List.of(
            "IIP-ALG04-a-idp-01", "IIP-ALG04-b-idp-01");
    private static final List<String> KEY_TRANSPORT = List.of(
            "IIP-ALG06-a-idp-01", "IIP-ALG06-b-idp-01", "IIP-ALG06-c-idp-01",
            "IIP-ALG06-d-idp-01");

    private final TestCase delegate;
    private final BrowserPrompt prompt;

    public SharedBrowserPolicyTestCase(TestCase delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!(delegate instanceof BrowserPrompt value)) {
            throw new IllegalArgumentException("Shared browser policy case requires a browser prompt");
        }
        if (!supports(delegate.id())) {
            throw new IllegalArgumentException("No shared browser policy for " + delegate.id());
        }
        prompt = value;
    }

    public static boolean supports(String caseId) {
        return BLOCK_ENCRYPTION.contains(caseId) || KEY_TRANSPORT.contains(caseId);
    }

    @Override public String id() { return delegate.id(); }
    @Override public TargetRole role() { return delegate.role(); }
    @Override public String browserInstructionsEn() { return prompt.browserInstructionsEn(); }
    @Override public String evidenceCampaignId() { return "crypto-policy"; }
    @Override public String evidenceCampaignTitle() { return "Cryptographic algorithm policy"; }
    @Override public RunCampaignQuery.ActionKind evidenceActionKind() {
        return RunCampaignQuery.ActionKind.CONFIGURATION;
    }
    @Override public List<String> evidenceActionKeys() {
        return List.of(BLOCK_ENCRYPTION.contains(id())
                ? "content-encryption-policy" : "key-transport-policy");
    }

    @Override public CaseStep start(CaseContext context) { return delegate.start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return delegate.resume(context, state, event);
    }
}

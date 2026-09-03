package com.samlscope.runner.cases;

import java.util.List;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.EvidenceCampaignCase;
import com.samlscope.runner.RunCampaignQuery;

/**
 * Keeps an approved browser case's outcome behavior while declaring a product-neutral policy
 * operation that can prepare several cases together. Completing the operation never supplies a
 * target outcome: the wrapped legacy browser case still finishes NOT_VERIFIED when no external
 * oracle is available.
 */
public final class SharedBrowserPolicyTestCase
        implements TestCase, BrowserPrompt, EvidenceCampaignCase, com.samlscope.runner.OperatorAssistedCase {
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

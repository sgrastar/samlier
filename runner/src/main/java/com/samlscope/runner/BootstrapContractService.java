package com.samlscope.runner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.samlscope.core.casedef.CaseDefinitionCatalog;
import com.samlscope.core.caseexec.CaseExecutionRepository;
import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.plan.MetadataDeliveryKind;
import com.samlscope.core.plan.PlanRepository;
import com.samlscope.core.run.RunRepository;
import com.samlscope.core.transcript.Direction;
import com.samlscope.core.transcript.TranscriptRecorder;

/** Groups per-case configuration waits into reusable environment contracts. */
public final class BootstrapContractService implements BootstrapContractQuery {
    private final CaseDefinitionCatalog definitions;
    private final CaseExecutionRepository executions;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final TranscriptRecorder transcript;
    private final MetadataLabService metadataLab;

    public BootstrapContractService(
            CaseDefinitionCatalog definitions,
            CaseExecutionRepository executions,
            PlanRepository plans,
            RunRepository runs,
            TranscriptRecorder transcript,
            MetadataLabService metadataLab) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.metadataLab = Objects.requireNonNull(metadataLab, "metadataLab");
    }

    @Override
    public List<BootstrapContract> contracts(String runId) {
        var run = runs.find(required(runId, "runId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        var grouped = new LinkedHashMap<ContractType, List<String>>();
        for (var execution : executions.list(runId)) {
            if (execution.status() != CaseExecutionStatus.WAITING_CONFIG) continue;
            var definition = definitions.require(execution.caseId());
            grouped.computeIfAbsent(classify(definition.obligation()), ignored -> new ArrayList<>())
                    .add(execution.caseId());
        }
        var metadataState = metadataLab.state(runId);
        var result = new ArrayList<BootstrapContract>();
        grouped.forEach((type, ids) -> {
            ids.sort(Comparator.naturalOrder());
            result.add(contract(type, ids, plan.suiteMetadataDelivery(), runId, metadataState));
        });
        result.sort(Comparator.comparing(BootstrapContract::id));
        return List.copyOf(result);
    }

    private BootstrapContract contract(
            ContractType type,
            List<String> ids,
            MetadataDeliveryKind delivery,
            String runId,
            MetadataLabService.State metadataState) {
        return switch (type) {
            case METADATA -> new BootstrapContract(
                    "metadata-feed",
                    "Suite-controlled metadata feed",
                    "Register one stable SAML metadata URL or MDQ source. SAMLscope changes fixtures behind that "
                            + "standard interface and derives evidence from metadata fetches and SAML traffic.",
                    Kind.STANDARD_METADATA,
                    metadataReadiness(delivery, runId),
                    metadataState.metadataUrl(),
                    delivery == MetadataDeliveryKind.MANUAL
                            ? "This Test Plan uses manual metadata delivery. Importing files can run individual "
                                    + "fixtures, but automatic rotation and refresh observation are unavailable."
                            : "Configure the target once with this Run-scoped metadata URL. Do not use a product "
                                    + "administration API. An endpoint fetch is recorded, but correlated SAML "
                                    + "traffic is still required before it counts as target evidence.",
                    ids.size(), ids);
            case CRYPTO -> operator(type, ids,
                    "Cryptographic policy",
                    "Prepare reusable signing, encryption, and algorithm-policy states. Protocol messages provide "
                            + "the evidence after this one-time setup.");
            case ATTRIBUTE -> operator(type, ids,
                    "Attribute release policy",
                    "Prepare fixed Test Peer entities with the required attribute-release policies once; cases "
                            + "reuse those entities instead of asking for the same setting repeatedly.");
            case AUTHENTICATION -> operator(type, ids,
                    "Authentication and identifier policy",
                    "Prepare the reusable authentication, NameID, and session-policy states needed by this Run. "
                            + "SAMLscope then exercises them through SAML messages.");
            case OTHER -> operator(type, ids,
                    "Other target capabilities",
                    "These cases require a target-side capability not exposed through a standard SAML management "
                            + "protocol. They remain explicit rather than being hidden behind a vendor API.");
        };
    }

    private BootstrapContract operator(ContractType type, List<String> ids, String title, String description) {
        return new BootstrapContract(
                type.id, title, description, Kind.OPERATOR_POLICY, Readiness.SETUP_REQUIRED,
                null, "Prepare this policy once for the listed cases. SAMLscope does not call a vendor Admin API.",
                ids.size(), ids);
    }

    private Readiness metadataReadiness(MetadataDeliveryKind delivery, String runId) {
        if (delivery == MetadataDeliveryKind.MANUAL) return Readiness.MANUAL_ONLY;
        var observed = transcript.list(runId).stream()
                .filter(entry -> entry.direction() == Direction.INBOUND)
                .anyMatch(entry -> "MetadataFetch".equals(entry.samlSummary().get("type"))
                        && "live".equals(entry.samlSummary().get("feed")));
        return observed ? Readiness.FETCH_OBSERVED : Readiness.SETUP_REQUIRED;
    }

    private ContractType classify(String obligation) {
        var requirement = obligation.contains(".")
                ? obligation.substring(0, obligation.indexOf('.')) : obligation;
        if (requirement.startsWith("IIP-MD")) return ContractType.METADATA;
        if (requirement.startsWith("IIP-ALG") || requirement.equals("IIP-IDP09")
                || isCryptoSso(obligation)) return ContractType.CRYPTO;
        if (List.of("IIP-IDP01", "IIP-IDP02", "IIP-IDP03", "IIP-IDP04", "IIP-IDP11")
                .contains(requirement)) return ContractType.ATTRIBUTE;
        if (requirement.startsWith("IIP-SSO") || requirement.startsWith("IIP-IDP")
                || requirement.startsWith("IIP-SP")) return ContractType.AUTHENTICATION;
        return ContractType.OTHER;
    }

    private boolean isCryptoSso(String obligation) {
        return obligation.matches("IIP-SSO01\\.(ez|fd|fe)(?:$|[-.].*)");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private enum ContractType {
        METADATA("metadata-feed"),
        CRYPTO("crypto-policy"),
        ATTRIBUTE("attribute-policy"),
        AUTHENTICATION("authentication-policy"),
        OTHER("other-capabilities");

        private final String id;
        ContractType(String id) { this.id = id; }
    }
}

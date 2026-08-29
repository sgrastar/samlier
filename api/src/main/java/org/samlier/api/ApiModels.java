package org.samlier.api;

import java.util.Map;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;

final class ApiModels {
    private ApiModels() {}

    record PlanWrite(
            String name,
            PlanProfile profile,
            TargetKind targetKind,
            String targetEntityId,
            MetadataSourceKind metadataSourceKind,
            String metadataSourceLocation,
            MetadataDeliveryKind suiteMetadataDelivery,
            Map<String, Boolean> declaredFeatures,
            TestPlan.Parameters parameters,
            TestPlan.Interaction interaction) {}

    record PlanView(TestPlan plan, String entityId, String metadataUrl, String mdqUrl) {}
    record RunCreated(org.samlier.core.run.TestRun run, String managementUrl) {}
    record ErrorView(String error, String message) {}
}

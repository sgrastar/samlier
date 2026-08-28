package org.samlier.saml;

import java.time.Instant;
import java.util.Map;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;

public final class SamlTestFixtures {
    private SamlTestFixtures() {}

    public static TestPlan idpPlan() {
        var now = Instant.parse("2026-08-29T00:00:00Z");
        return new TestPlan("plan_0123456789ABCDEFGHJKMNPQRS", "IdP", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.HTTP_URL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
    }
}

package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import org.samlier.core.plan.TestPlan;

@FunctionalInterface
public interface TargetSigningCertificateProvider {
    List<X509Certificate> certificatesFor(TestPlan plan, String runId);
}

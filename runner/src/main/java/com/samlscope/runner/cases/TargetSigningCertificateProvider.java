package com.samlscope.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import com.samlscope.core.plan.TestPlan;

@FunctionalInterface
public interface TargetSigningCertificateProvider {
    List<X509Certificate> certificatesFor(TestPlan plan, String runId);
}

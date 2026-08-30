package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import org.samlier.core.plan.TestPlan;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.store.MetadataCache;

/** Resolves target verification keys from the exact metadata cached during preflight. */
public final class CachedTargetSigningCertificateProvider implements TargetSigningCertificateProvider {
    private final MetadataCache cache;
    private final TargetMetadataParser parser;

    public CachedTargetSigningCertificateProvider(MetadataCache cache, TargetMetadataParser parser) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public List<X509Certificate> certificatesFor(TestPlan plan, String runId) {
        Objects.requireNonNull(plan, "plan");
        return parser.parse(
                cache.getRunSnapshot(runId, plan.id()), plan.target().entityId())
                .signingCertificates();
    }
}

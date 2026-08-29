package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.runner.TestCaseRegistry;

/** Complete approved M3 AUTOMATED implementation slice. */
public final class M3AutomatedCaseRegistry {
    private M3AutomatedCaseRegistry() {}

    public static TestCaseRegistry create(
            Function<String, byte[]> metadata,
            TranscriptContentReader transcriptContent,
            Function<String, List<X509Certificate>> certificates) {
        var cases = new ArrayList<org.samlier.core.caseexec.TestCase>();
        DiscoveryMetadataTestCase.approvedIds().stream()
                .map(id -> new DiscoveryMetadataTestCase(id, metadata)).forEach(cases::add);
        LogoutTranscriptTestCase.approvedIds().stream()
                .map(id -> new LogoutTranscriptTestCase(id, transcriptContent)).forEach(cases::add);
        EcpTranscriptTestCase.approvedIds().stream()
                .map(id -> new EcpTranscriptTestCase(id, transcriptContent, certificates)).forEach(cases::add);
        return new TestCaseRegistry(cases);
    }
}

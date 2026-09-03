package com.samlscope.runner.cases;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.runner.TestCaseRegistry;

/** Complete approved M3 AUTOMATED implementation slice. */
public final class M3AutomatedCaseRegistry {
    private M3AutomatedCaseRegistry() {}

    public static TestCaseRegistry create(
            Function<String, byte[]> metadata,
            TranscriptContentReader transcriptContent,
            Function<String, List<X509Certificate>> certificates,
            SamlDecryptionKeyProvider decryptionKeys) {
        var cases = new ArrayList<com.samlscope.core.caseexec.TestCase>();
        DiscoveryMetadataTestCase.approvedIds().stream()
                .map(id -> new DiscoveryMetadataTestCase(id, metadata)).forEach(cases::add);
        LogoutTranscriptTestCase.approvedIds().stream()
                .map(id -> new LogoutTranscriptTestCase(id, transcriptContent, certificates)).forEach(cases::add);
        EcpTranscriptTestCase.approvedIds().stream()
                .map(id -> new EcpTranscriptTestCase(id, transcriptContent, certificates, decryptionKeys))
                .forEach(cases::add);
        return new TestCaseRegistry(cases);
    }
}

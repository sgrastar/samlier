package org.samlier.runner.result;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReportHtmlWriterTest {
    @Test
    void embedsJsonAsBase64AndNeverInterpolatesTargetTextIntoMarkup() {
        var dangerous = "</script><img src=x onerror=alert(1)>";
        var json = ("{\"run\":{\"conformance\":\"INDETERMINATE\",\"completeness\":\"INCOMPLETE\"},"
                + "\"coverage\":{\"must_resolved\":0,\"must_observable\":1},"
                + "\"target\":{\"declared_product\":\"" + dangerous.replace("\"", "\\\"") + "\"},"
                + "\"profile\":{\"id\":\"IIP\"},\"evaluation_bundle\":{\"digest\":\"sha256:x\"},"
                + "\"conformance_statement\":\"statement\",\"requirements\":[]}")
                .getBytes(StandardCharsets.UTF_8);

        var html = new String(new ReportHtmlWriter().write(json), StandardCharsets.UTF_8);

        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("Content-Security-Policy"));
        assertTrue(html.contains("textContent"));
        assertFalse(html.contains(dangerous));
        assertFalse(html.contains("__SAMLIER_RESULT_BASE64__"));
    }
}

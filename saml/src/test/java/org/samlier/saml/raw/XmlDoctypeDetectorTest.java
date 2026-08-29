package org.samlier.saml.raw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class XmlDoctypeDetectorTest {
    @Test
    void detectsInternalAndExternalDtdsWithoutResolvingThem() {
        assertTrue(XmlDoctypeDetector.containsDoctype(bytes(
                "<!DOCTYPE root [<!ELEMENT root EMPTY>]><root/>")));
        assertTrue(XmlDoctypeDetector.containsDoctype(bytes(
                "<!DOCTYPE root SYSTEM 'https://invalid.example/never-fetch.dtd'><root/>")));
    }

    @Test
    void doesNotTreatCommentTextAsADtd() {
        assertFalse(XmlDoctypeDetector.containsDoctype(bytes(
                "<?xml version='1.0'?><root><!-- <!DOCTYPE fake> --></root>")));
    }

    @Test
    void detectsUtf16Documents() {
        var xml = "<!DOCTYPE root [<!ELEMENT root EMPTY>]><root/>".getBytes(StandardCharsets.UTF_16);
        assertTrue(XmlDoctypeDetector.containsDoctype(xml));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

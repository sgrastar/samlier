package com.samlscope.saml.raw;

/**
 * Separate low-level generation boundary for intentionally non-standard XML.
 * M0 does not expose abnormal-message generation; G2/M1 cases provide implementations.
 */
public interface RawMessageBuilder {
    byte[] build(RawMessageSpec specification);

    record RawMessageSpec(String fixtureId, byte[] baseDocument) {
        public RawMessageSpec {
            baseDocument = baseDocument.clone();
        }
    }
}

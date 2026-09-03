package com.samlscope.saml.raw;

import java.nio.charset.StandardCharsets;

/** Concrete abnormal-message boundary. It deliberately performs no XML normalization. */
public final class BytePreservingRawMessageBuilder implements RawMessageBuilder {
    @Override
    public byte[] build(RawMessageSpec specification) {
        if (specification == null) throw new IllegalArgumentException("specification is required");
        return specification.baseDocument();
    }

    /** Encode a raw text value while applying XML-oriented code-point boundaries. */
    public byte[] encodeValue(String value, int maximumCodePoints) {
        if (value == null || maximumCodePoints < 0) throw new IllegalArgumentException("invalid value boundary");
        var count = value.codePointCount(0, value.length());
        if (count > maximumCodePoints) throw new IllegalArgumentException("value exceeds code-point boundary");
        if (value.codePoints().anyMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("isolated surrogate is not an XML character");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

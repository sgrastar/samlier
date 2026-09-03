package com.samlscope.saml.normal;

public final class SamlException extends RuntimeException {
    public SamlException(String message) { super(message); }
    public SamlException(String message, Throwable cause) { super(message, cause); }
}

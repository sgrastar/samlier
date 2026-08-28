package org.samlier.saml.crypto;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record PlanCredentials(PrivateKey privateKey, X509Certificate certificate) {}

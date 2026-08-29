package org.samlier.saml.normal;

import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

/** Offline, DTD-free validation against the SAML 2.0 schemas shipped with the pinned OpenSAML runtime. */
public final class SamlSchemaValidation {
    public enum SchemaKind { PROTOCOL, ASSERTION }

    private static final Map<String, String> NAMESPACE_RESOURCES = Map.of(
            "urn:oasis:names:tc:SAML:2.0:protocol", "schema/saml-schema-protocol-2.0.xsd",
            "urn:oasis:names:tc:SAML:2.0:assertion", "schema/saml-schema-assertion-2.0.xsd",
            "http://www.w3.org/2000/09/xmldsig#", "schema/xmldsig-core-schema.xsd",
            "http://www.w3.org/2001/04/xmlenc#", "schema/xenc-schema.xsd");
    private static final Schema PROTOCOL = load("urn:oasis:names:tc:SAML:2.0:protocol");
    private static final Schema ASSERTION = load("urn:oasis:names:tc:SAML:2.0:assertion");

    private SamlSchemaValidation() {}

    public static boolean isValid(Element element, SchemaKind kind) {
        try {
            var validator = (kind == SchemaKind.PROTOCOL ? PROTOCOL : ASSERTION).newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new DOMSource(element));
            return true;
        } catch (SAXException | java.io.IOException invalid) {
            return false;
        }
    }

    private static Schema load(String namespace) {
        try {
            var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new ClasspathResolver());
            var resource = NAMESPACE_RESOURCES.get(namespace);
            var stream = requiredResource(resource);
            var source = new StreamSource(stream, "classpath:/" + resource);
            return factory.newSchema(source);
        } catch (SAXException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static InputStream requiredResource(String resource) {
        var stream = SamlSchemaValidation.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing pinned schema resource: " + resource);
        return stream;
    }

    private static final class ClasspathResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(
                String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            var resource = NAMESPACE_RESOURCES.get(namespaceURI);
            if (resource == null) return null;
            return new Input(publicId, systemId, "classpath:/" + resource, requiredResource(resource));
        }
    }

    private static final class Input implements LSInput {
        private String publicId;
        private String systemId;
        private String baseUri;
        private InputStream byteStream;
        private String encoding;
        private String stringData;
        private Reader characterStream;
        private boolean certifiedText;

        private Input(String publicId, String systemId, String baseUri, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseUri = baseUri;
            this.byteStream = byteStream;
        }

        @Override public Reader getCharacterStream() { return characterStream; }
        @Override public void setCharacterStream(Reader value) { characterStream = value; }
        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream value) { byteStream = value; }
        @Override public String getStringData() { return stringData; }
        @Override public void setStringData(String value) { stringData = value; }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String value) { systemId = value; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String value) { publicId = value; }
        @Override public String getBaseURI() { return baseUri; }
        @Override public void setBaseURI(String value) { baseUri = value; }
        @Override public String getEncoding() { return encoding; }
        @Override public void setEncoding(String value) { encoding = value; }
        @Override public boolean getCertifiedText() { return certifiedText; }
        @Override public void setCertifiedText(boolean value) { certifiedText = value; }
    }
}

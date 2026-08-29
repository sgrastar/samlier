package org.samlier.saml.raw;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/** Detects an actual DTD declaration without resolving or expanding any entity. */
public final class XmlDoctypeDetector {
    private static final int MAX_XML_BYTES = 5 * 1024 * 1024;

    private XmlDoctypeDetector() {}

    public static boolean containsDoctype(byte[] xml) {
        if (xml == null) throw new IllegalArgumentException("xml is required");
        if (xml.length > MAX_XML_BYTES) throw new IllegalArgumentException("XML exceeds the 5 MiB limit");
        try {
            var factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", new DtdHandler());
            reader.parse(new InputSource(new ByteArrayInputStream(xml)));
            return false;
        } catch (DtdDetected expected) {
            return true;
        } catch (SAXException e) {
            // A malformed document is not converted into a G03 violation. Other XML obligations own that verdict.
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect XML for a DTD", e);
        }
    }

    private static final class DtdHandler extends DefaultHandler2 {
        @Override
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            throw new DtdDetected();
        }
    }

    private static final class DtdDetected extends SAXException {}
}

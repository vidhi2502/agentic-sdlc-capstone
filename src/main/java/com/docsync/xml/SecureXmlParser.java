package com.docsync.xml;

import java.io.IOException;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Hardened DOM parsing shared by every XML reader in the tool. Enables
 * {@code FEATURE_SECURE_PROCESSING} and disables DOCTYPE declarations and
 * external entities so XXE attacks are impossible (architecture §6).
 */
public final class SecureXmlParser {

    private SecureXmlParser() {
    }

    /**
     * Parses the given XML file with a securely configured JDK DOM parser.
     *
     * @param file the XML file to parse
     * @return the parsed document
     * @throws IOException  when the file cannot be read
     * @throws SAXException when the content is not well-formed XML or uses a
     *                      forbidden construct such as a DOCTYPE declaration
     */
    public static Document parse(Path file) throws IOException, SAXException {
        return parse(file, false);
    }

    /**
     * Parses an XML file whose format legitimately declares a DOCTYPE, such as
     * JaCoCo reports. The DOCTYPE is tolerated but external DTDs and entities
     * are never loaded, so XXE remains impossible.
     *
     * @param file the XML file to parse
     * @return the parsed document
     * @throws IOException  when the file cannot be read
     * @throws SAXException when the content is not well-formed XML
     */
    public static Document parseAllowingDoctype(Path file) throws IOException, SAXException {
        return parse(file, true);
    }

    private static Document parse(Path file, boolean allowDoctype) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !allowDoctype);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new QuietErrorHandler());
            return builder.parse(file.toFile());
        } catch (ParserConfigurationException e) {
            throw new SAXException("Secure XML parser could not be configured", e);
        }
    }

    /**
     * Rethrows parse problems as exceptions instead of printing them to
     * stderr, so callers stay in control of all user-facing output (NFR-3).
     */
    private static final class QuietErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) {
            // Non-fatal parser warnings are intentionally ignored.
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}

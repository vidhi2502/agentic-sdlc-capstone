package com.docsync.scan;

import com.docsync.model.Dependency;
import com.docsync.model.NotFound;
import com.docsync.xml.SecureXmlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Extracts project metadata from {@code pom.xml} with the hardened JDK DOM
 * parser (FR-4, FR-5, FR-6). Performs one level of {@code ${property}}
 * interpolation against the project's own {@code <properties>}; unresolvable
 * placeholders and parent-managed (absent) versions degrade to {@code Not
 * Found} (DR-7). A missing or unparseable {@code pom.xml} degrades every field
 * (FR-9, FR-10).
 */
public final class PomParser {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Parses {@code pom.xml} in the given project root.
     *
     * @param projectRoot the folder being scanned
     * @return the extracted metadata; never {@code null}, fields degrade to
     *         {@code null}/empty with a warning instead of throwing
     */
    public PomInfo parse(Path projectRoot) {
        Path pomFile = projectRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            return PomInfo.notFound("pom.xml not found in " + projectRoot);
        }
        Document document;
        try {
            document = SecureXmlParser.parse(pomFile);
        } catch (Exception e) {
            return PomInfo.notFound("pom.xml could not be parsed: " + e.getMessage());
        }
        return extract(document.getDocumentElement());
    }

    private PomInfo extract(Element project) {
        Map<String, String> properties = readProperties(project);

        String name = interpolate(directChildText(project, "name"), properties);
        if (name == null) {
            name = interpolate(directChildText(project, "artifactId"), properties);
        }
        String description = interpolate(directChildText(project, "description"), properties);
        String javaVersion = properties.get("maven.compiler.source");
        if (javaVersion == null) {
            javaVersion = properties.get("java.version");
        }
        javaVersion = interpolate(javaVersion, properties);

        List<Dependency> dependencies = readDependencies(project, properties);
        return new PomInfo(name, description, javaVersion, "Maven", dependencies, List.of());
    }

    private Map<String, String> readProperties(Element project) {
        Map<String, String> properties = new HashMap<>();
        Element propertiesElement = directChild(project, "properties");
        if (propertiesElement == null) {
            return properties;
        }
        NodeList children = propertiesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                properties.put(node.getNodeName(), node.getTextContent().trim());
            }
        }
        return properties;
    }

    private List<Dependency> readDependencies(Element project, Map<String, String> properties) {
        List<Dependency> dependencies = new ArrayList<>();
        Element dependenciesElement = directChild(project, "dependencies");
        if (dependenciesElement == null) {
            return dependencies;
        }
        NodeList children = dependenciesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("dependency")) {
                dependencies.add(readDependency((Element) node, properties));
            }
        }
        return dependencies;
    }

    private Dependency readDependency(Element dependencyElement, Map<String, String> properties) {
        String groupId = interpolate(directChildText(dependencyElement, "groupId"), properties);
        String artifactId = interpolate(directChildText(dependencyElement, "artifactId"), properties);
        String version = interpolate(directChildText(dependencyElement, "version"), properties);
        String scope = directChildText(dependencyElement, "scope");
        return new Dependency(coordinate(groupId, artifactId), version, scope);
    }

    private String coordinate(String groupId, String artifactId) {
        if (groupId == null && artifactId == null) {
            return null;
        }
        return (groupId == null ? NotFound.TEXT : groupId) + ":" + (artifactId == null ? NotFound.TEXT : artifactId);
    }

    /**
     * Resolves {@code ${property}} placeholders exactly once against the
     * project's own properties. Any unresolvable placeholder makes the whole
     * value {@code Not Found} (DR-7).
     */
    private String interpolate(String raw, Map<String, String> properties) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String replacement = properties.get(matcher.group(1));
            if (replacement == null) {
                return null;
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        String result = resolved.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private Element directChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(childName)) {
                return (Element) node;
            }
        }
        return null;
    }

    private String directChildText(Element parent, String childName) {
        Element child = directChild(parent, childName);
        if (child == null) {
            return null;
        }
        String text = child.getTextContent().trim();
        return text.isEmpty() ? null : text;
    }
}

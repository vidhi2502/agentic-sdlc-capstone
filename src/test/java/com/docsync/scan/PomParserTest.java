package com.docsync.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.model.Dependency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link PomParser} extraction, interpolation and
 * degradation (FR-4, FR-5, FR-6, FR-9, DR-7).
 */
class PomParserTest {

    private final PomParser parser = new PomParser();

    @TempDir
    Path projectRoot;

    private void writePom(String content) throws IOException {
        Files.writeString(projectRoot.resolve("pom.xml"), content);
    }

    @Test
    void extractsAllFieldsFromCompletePom() throws IOException {
        writePom("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <artifactId>demo-app</artifactId>
                  <name>Demo App</name>
                  <description>A demo application</description>
                  <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                    <junit.version>5.10.2</junit.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>ignored</groupId>
                        <artifactId>ignored</artifactId>
                        <version>9.9</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>${junit.version}</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.0.0-jre</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        PomInfo info = parser.parse(projectRoot);

        assertEquals("Demo App", info.name());
        assertEquals("A demo application", info.description());
        assertEquals("17", info.javaVersion());
        assertEquals("Maven", info.buildTool());
        assertEquals(2, info.dependencies().size());
        Dependency junit = info.dependencies().get(0);
        assertEquals("org.junit.jupiter:junit-jupiter", junit.groupArtifact());
        assertEquals("5.10.2", junit.version());
        assertEquals("test", junit.scope());
        Dependency guava = info.dependencies().get(1);
        assertEquals("compile", guava.scope());
        assertTrue(info.warnings().isEmpty());
    }

    @Test
    void fallsBackToArtifactIdWhenNameMissing() throws IOException {
        writePom("""
                <project>
                  <artifactId>my-artifact</artifactId>
                </project>
                """);

        assertEquals("my-artifact", parser.parse(projectRoot).name());
    }

    @Test
    void readsJavaVersionFromJavaVersionProperty() throws IOException {
        writePom("""
                <project>
                  <artifactId>a</artifactId>
                  <properties>
                    <java.version>21</java.version>
                  </properties>
                </project>
                """);

        assertEquals("21", parser.parse(projectRoot).javaVersion());
    }

    @Test
    void returnsNotFoundFieldsWhenPomMissing() {
        PomInfo info = parser.parse(projectRoot);

        assertNull(info.name());
        assertNull(info.description());
        assertNull(info.javaVersion());
        assertNull(info.buildTool());
        assertTrue(info.dependencies().isEmpty());
        assertFalse(info.warnings().isEmpty());
    }

    @Test
    void returnsNotFoundFieldsWhenPomUnparseable() throws IOException {
        writePom("<project><name>broken");

        PomInfo info = parser.parse(projectRoot);

        assertNull(info.name());
        assertNull(info.buildTool());
        assertFalse(info.warnings().isEmpty());
    }

    @Test
    void rejectsDoctypeDeclarationsAsUnparseable() throws IOException {
        writePom("""
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <project><name>&xxe;</name></project>
                """);

        PomInfo info = parser.parse(projectRoot);

        assertNull(info.name());
        assertFalse(info.warnings().isEmpty());
    }

    @Test
    void parentManagedVersionBecomesNotFound() throws IOException {
        writePom("""
                <project>
                  <artifactId>a</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>g</groupId>
                      <artifactId>managed</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        Dependency dependency = parser.parse(projectRoot).dependencies().get(0);

        assertEquals("Not Found", dependency.versionOrNotFound());
    }

    @Test
    void unresolvablePlaceholderBecomesNotFound() throws IOException {
        writePom("""
                <project>
                  <artifactId>a</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>g</groupId>
                      <artifactId>x</artifactId>
                      <version>${undefined.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        Dependency dependency = parser.parse(projectRoot).dependencies().get(0);

        assertEquals("Not Found", dependency.versionOrNotFound());
    }
}

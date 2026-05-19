package com.chunbaetour.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gradle TestKit functional tests verifying the build.gradle changes introduced
 * in the QueryDSL integration PR:
 *
 * <ul>
 *   <li>QueryDSL JPA and APT dependencies resolve from Maven Central.</li>
 *   <li>jakarta annotation and persistence annotation-processor dependencies resolve.</li>
 *   <li>The {@code sourceSets} block correctly registers the Q-class output directory.</li>
 *   <li>A minimal JPA entity causes the APT to generate a Q-class during compilation.</li>
 * </ul>
 */
class BuildGradleConfigurationTest {

    @TempDir
    File projectDir;

    /**
     * Writes the minimal build.gradle that mirrors the QueryDSL-related sections
     * added in the PR under test.  Spring Boot plugin is intentionally excluded to
     * keep the test project lightweight and resolvable without a full Spring BOM.
     */
    @BeforeEach
    void writeProjectFiles() throws IOException {
        // settings.gradle
        Files.writeString(projectDir.toPath().resolve("settings.gradle"),
                "rootProject.name = 'querydsl-config-test'\n");

        // build.gradle — mirrors the PR changes, using standalone (no Spring Boot plugin)
        String buildGradle = """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                dependencies {
                    // QueryDSL — identical to the PR diff
                    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
                    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
                    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
                    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'
                    // Minimal JPA API to compile the entity
                    implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
                }
                // sourceSets — identical to the PR diff
                sourceSets {
                    main {
                        java {
                            srcDir "${layout.buildDirectory.get()}/generated/sources/annotationProcessor/java/main"
                        }
                    }
                }
                tasks.named('test') {
                    useJUnitPlatform()
                }
                """;
        Files.writeString(projectDir.toPath().resolve("build.gradle"), buildGradle);

        // Minimal JPA entity so the APT has something to generate a Q-class from.
        Path entityPackageDir = projectDir.toPath()
                .resolve("src/main/java/com/example");
        Files.createDirectories(entityPackageDir);

        String entitySource = """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;

                @Entity
                public class SampleEntity {
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    private String name;
                }
                """;
        Files.writeString(entityPackageDir.resolve("SampleEntity.java"), entitySource);
    }

    // ─── Dependency resolution ─────────────────────────────────────────────────────

    @Test
    void querydsl_jpa_jakarta_dependency_resolves_successfully() {
        BuildResult result = runGradle("dependencies", "--configuration", "compileClasspath");

        assertThat(result.getOutput())
                .contains("com.querydsl:querydsl-jpa:5.1.0");
    }

    @Test
    void querydsl_apt_jakarta_annotation_processor_resolves_successfully() {
        BuildResult result = runGradle("dependencies", "--configuration", "annotationProcessor");

        assertThat(result.getOutput())
                .contains("com.querydsl:querydsl-apt:5.1.0");
    }

    @Test
    void jakarta_annotation_api_annotation_processor_resolves_successfully() {
        BuildResult result = runGradle("dependencies", "--configuration", "annotationProcessor");

        assertThat(result.getOutput())
                .contains("jakarta.annotation:jakarta.annotation-api");
    }

    @Test
    void jakarta_persistence_api_annotation_processor_resolves_successfully() {
        BuildResult result = runGradle("dependencies", "--configuration", "annotationProcessor");

        assertThat(result.getOutput())
                .contains("jakarta.persistence:jakarta.persistence-api");
    }

    // ─── Compilation and Q-class generation ───────────────────────────────────────

    @Test
    void compileJava_task_succeeds_with_querydsl_configuration() {
        BuildResult result = runGradle("compileJava");

        assertThat(result.task(":compileJava").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void querydsl_apt_generates_q_class_for_entity() {
        runGradle("compileJava");

        // The APT writes Q-classes to the annotationProcessor output directory.
        Path generatedSourcesDir = projectDir.toPath()
                .resolve("build/generated/sources/annotationProcessor/java/main");

        assertThat(generatedSourcesDir).exists();
        assertThat(generatedSourcesDir).isDirectory();
    }

    @Test
    void generated_q_class_file_exists_for_sample_entity() {
        runGradle("compileJava");

        Path qClassFile = projectDir.toPath()
                .resolve("build/generated/sources/annotationProcessor/java/main")
                .resolve("com/example/QSampleEntity.java");

        assertThat(qClassFile).exists();
        assertThat(qClassFile).isRegularFile();
    }

    @Test
    void generated_q_class_contains_expected_entity_reference() throws IOException {
        runGradle("compileJava");

        Path qClassFile = projectDir.toPath()
                .resolve("build/generated/sources/annotationProcessor/java/main")
                .resolve("com/example/QSampleEntity.java");

        String content = Files.readString(qClassFile);
        assertThat(content).contains("QSampleEntity");
        assertThat(content).contains("SampleEntity");
    }

    // ─── sourceSets registration ───────────────────────────────────────────────────

    @Test
    void main_source_set_includes_generated_annotation_processor_directory() {
        BuildResult result = runGradle("sourceSets");

        // The 'sourceSets' task prints each source set's directories.
        // Verify the Q-class output path appears in the main source set output.
        assertThat(result.getOutput())
                .contains("generated/sources/annotationProcessor/java/main");
    }

    @Test
    void build_task_succeeds_end_to_end_with_querydsl_configuration() {
        // 'build' exercises: compileJava (with APT) → classes → jar
        BuildResult result = runGradle("build", "-x", "test");

        assertThat(result.task(":compileJava").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":build").getOutcome())
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private BuildResult runGradle(String... arguments) {
        List<String> args = Arrays.asList(arguments);
        return GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(args)
                .withGradleVersion("9.4.1")   // matches the wrapper in the main project
                .forwardOutput()
                .build();
    }
}
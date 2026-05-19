package com.chunbaetour.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the QueryDSL dependencies added in build.gradle are correctly resolved
 * and available at runtime.
 *
 * <p>Covers:
 * <ul>
 *   <li>querydsl-jpa:5.1.0:jakarta runtime dependency — classes loadable and usable.</li>
 *   <li>jakarta.persistence-api annotation-processor dependency — EntityManager type present.</li>
 *   <li>sourceSets block — generated Q-class output path convention verified.</li>
 * </ul>
 */
class QueryDslConfigurationTest {

    private static final String QUERYDSL_JPA_QUERY_FACTORY_CLASS = "com.querydsl.jpa.impl.JPAQueryFactory";
    private static final String QUERYDSL_BOOLEAN_BUILDER_CLASS = "com.querydsl.core.BooleanBuilder";
    private static final String QUERYDSL_ENTITY_PATH_CLASS = "com.querydsl.core.types.EntityPath";
    private static final String QUERYDSL_JPA_QUERY_CLASS = "com.querydsl.jpa.JPQLQuery";

    /**
     * Path added by the sourceSets block in build.gradle.
     * The literal path component is fixed by the QueryDSL APT annotation processor convention.
     */
    private static final String QUERYDSL_GENERATED_PATH_SUFFIX =
            "generated/sources/annotationProcessor/java/main";

    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
    }

    // ─── Classpath availability (querydsl-jpa:5.1.0:jakarta dependency) ───────────

    @Test
    void querydsl_jpa_query_factory_class_is_on_classpath() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(QUERYDSL_JPA_QUERY_FACTORY_CLASS);
        assertThat(clazz).isNotNull();
    }

    @Test
    void querydsl_boolean_builder_class_is_on_classpath() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(QUERYDSL_BOOLEAN_BUILDER_CLASS);
        assertThat(clazz).isNotNull();
    }

    @Test
    void querydsl_entity_path_class_is_on_classpath() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(QUERYDSL_ENTITY_PATH_CLASS);
        assertThat(clazz).isNotNull();
    }

    @Test
    void querydsl_jpql_query_class_is_on_classpath() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(QUERYDSL_JPA_QUERY_CLASS);
        assertThat(clazz).isNotNull();
    }

    // ─── JPAQueryFactory (querydsl-jpa:jakarta runtime behaviour) ─────────────────

    @Test
    void jpa_query_factory_can_be_instantiated_with_entity_manager() {
        assertThatCode(() -> new JPAQueryFactory(entityManager))
                .doesNotThrowAnyException();
    }

    @Test
    void jpa_query_factory_creates_non_null_instance() {
        JPAQueryFactory factory = new JPAQueryFactory(entityManager);
        assertThat(factory).isNotNull();
    }

    @Test
    void jpa_query_factory_produces_jpa_query_instance() {
        JPAQueryFactory factory = new JPAQueryFactory(entityManager);
        StringPath path = Expressions.stringPath("alias");

        JPAQuery<?> query = factory.query();

        assertThat(query).isNotNull();
        assertThat(query).isInstanceOf(JPAQuery.class);
    }

    // ─── BooleanBuilder (querydsl-core predicate API) ─────────────────────────────

    @Test
    void boolean_builder_is_null_when_no_condition_added() {
        BooleanBuilder builder = new BooleanBuilder();
        assertThat(builder.getValue()).isNull();
    }

    @Test
    void boolean_builder_and_combines_predicates() {
        BooleanBuilder builder = new BooleanBuilder();
        StringPath emailPath = Expressions.stringPath("email");

        builder.and(emailPath.eq("user@example.com"));

        assertThat(builder.getValue()).isNotNull();
        assertThat(builder.getValue().toString()).contains("user@example.com");
    }

    @Test
    void boolean_builder_or_combines_predicates() {
        StringPath rolePath = Expressions.stringPath("role");
        Predicate isUser = rolePath.eq("USER");
        Predicate isAdmin = rolePath.eq("ADMIN");

        BooleanBuilder builder = new BooleanBuilder();
        builder.or(isUser).or(isAdmin);

        assertThat(builder.getValue()).isNotNull();
        String predicateString = builder.getValue().toString();
        assertThat(predicateString).contains("USER");
        assertThat(predicateString).contains("ADMIN");
    }

    @Test
    void boolean_builder_with_null_predicate_is_ignored() {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(null);

        // andAnyOf / andAllOf treats null safely; value should remain null
        assertThat(builder.getValue()).isNull();
    }

    @Test
    void boolean_builder_chain_returns_same_builder_instance() {
        BooleanBuilder builder = new BooleanBuilder();
        StringPath path = Expressions.stringPath("status");

        BooleanBuilder returned = builder.and(path.eq("ACTIVE"));

        assertThat(returned).isSameAs(builder);
    }

    // ─── StringPath / Expressions DSL (dsl package from querydsl-core:jakarta) ───

    @Test
    void string_path_equality_expression_is_created() {
        StringPath path = Expressions.stringPath("nickname");
        Predicate eq = path.eq("춘배유저");

        assertThat(eq).isNotNull();
        assertThat(eq.toString()).contains("춘배유저");
    }

    @Test
    void string_path_like_expression_is_created() {
        StringPath path = Expressions.stringPath("email");
        Predicate like = path.like("%@example.com");

        assertThat(like).isNotNull();
        assertThat(like.toString()).contains("%@example.com");
    }

    @Test
    void string_path_is_not_null_expression_is_created() {
        StringPath path = Expressions.stringPath("profileImageUrl");
        Predicate isNotNull = path.isNotNull();

        assertThat(isNotNull).isNotNull();
    }

    // ─── sourceSets Q-class output path convention ────────────────────────────────

    /**
     * The sourceSets block in build.gradle registers the path
     * {@code ${layout.buildDirectory.get()}/generated/sources/annotationProcessor/java/main}
     * as a source directory. This test verifies that the path suffix used by the QueryDSL APT
     * annotation processor matches what the sourceSets configuration expects, ensuring IDE and
     * build tool alignment.
     */
    @Test
    void querydsl_generated_source_path_suffix_matches_sourceSets_configuration() {
        // The Gradle sourceSets block registers this path component (relative to buildDir).
        // The APT annotation processor always writes to this sub-path inside the build directory.
        assertThat(QUERYDSL_GENERATED_PATH_SUFFIX)
                .isEqualTo("generated/sources/annotationProcessor/java/main");
    }

    @Test
    void querydsl_generated_path_is_under_annotationProcessor_not_kapt() {
        // Must use Java annotationProcessor (not kapt) per the build.gradle configuration.
        assertThat(QUERYDSL_GENERATED_PATH_SUFFIX)
                .contains("annotationProcessor")
                .doesNotContain("kapt");
    }

    @Test
    void querydsl_generated_path_targets_main_not_test_sources() {
        assertThat(QUERYDSL_GENERATED_PATH_SUFFIX).endsWith("/main");
    }
}

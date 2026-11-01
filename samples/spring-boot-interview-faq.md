# Spring Boot Interview FAQ

A compact set of questions and answers I use when preparing developers for Spring Boot interviews.
Upload this file to the RAG chatbot and ask it questions to see grounded answers with sources.

## What is Spring Boot and why use it?

Spring Boot is an opinionated layer on top of the Spring Framework. It removes most boilerplate through
auto-configuration, starter dependencies and an embedded server (Tomcat by default), so a production-ready
service can be packaged as a single executable JAR and started with `java -jar`.

## How does auto-configuration work?

Auto-configuration classes are listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Each class is guarded by
conditions such as `@ConditionalOnClass`, `@ConditionalOnMissingBean` and `@ConditionalOnProperty`. A bean is
only created when its conditions match, and any bean you declare yourself takes precedence because of
`@ConditionalOnMissingBean`. Run the app with `--debug` to print the condition evaluation report.

## What are Spring Boot starters?

Starters are curated dependency descriptors, for example `spring-boot-starter-web` or
`spring-boot-starter-data-jpa`. Adding one starter brings in a tested, compatible set of libraries whose
versions are managed by the Spring Boot parent or BOM.

## Constructor injection vs field injection?

Constructor injection is the recommended style. It makes dependencies explicit, allows fields to be `final`,
makes classes easy to unit test without a Spring context and fails fast when a dependency is missing.
Field injection with `@Autowired` hides dependencies and needs reflection in tests.

## What is the difference between @Component, @Service and @Repository?

All three register a bean through component scanning. `@Service` marks business logic and `@Repository`
marks persistence code; `@Repository` also enables translation of persistence exceptions into Spring's
`DataAccessException` hierarchy.

## How do you externalise configuration?

Use `application.yml` or `application.properties`, environment variables and command-line arguments.
Profiles such as `dev` and `prod` are activated with `spring.profiles.active`. Bind related settings to a
type-safe record annotated with `@ConfigurationProperties` and validate it with `@Validated`.

## What does Spring Boot Actuator provide?

Actuator adds production endpoints such as `/actuator/health`, `/actuator/info` and `/actuator/metrics`.
Metrics are collected with Micrometer and can be exported to Prometheus, Datadog and other back ends.

## How should a REST API report errors?

Use a global `@RestControllerAdvice` and return `ProblemDetail` objects, the RFC 9457 standard supported
natively since Spring Framework 6. Each response carries a `type`, `title`, `status` and `detail`, and can
include extra properties such as field validation errors.

## How do you handle transactions?

Annotate service methods with `@Transactional`. By default only unchecked exceptions trigger a rollback.
Self-invocation bypasses the proxy, so calling a transactional method from the same class does not start a
new transaction.

## What is the N+1 select problem in JPA?

It happens when loading a list of entities triggers one extra query per entity to fetch a lazy association.
Fix it with `JOIN FETCH` queries, `@EntityGraph` or batch fetching (`hibernate.default_batch_fetch_size`).

## How do you test a Spring Boot application?

Use plain JUnit 5 and Mockito for unit tests, `@WebMvcTest` with `MockMvc` for the web layer,
`@DataJpaTest` for repositories and `@SpringBootTest` for full integration tests. Testcontainers runs real
databases or brokers in Docker for realistic integration tests.

## What is Spring AI?

Spring AI brings portable abstractions for AI applications to the Spring ecosystem: `ChatClient` for chat
models, `EmbeddingModel` for embeddings and `VectorStore` for similarity search across stores such as
pgvector, Redis, Pinecone or the in-memory `SimpleVectorStore`. It is the natural way to build
Retrieval-Augmented Generation (RAG) features in a Spring Boot service.

plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}
val resilience4jVersion = "2.4.0"
val wiremockSpringBootVersion = "4.2.1"
dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-restclient") // RestClient / HTTP interface clients for calling the payment provider
    implementation("org.springframework.boot:spring-boot-starter-validation") // validate requests & webhook payloads

    // ---------- Security: authentication & authorization ----------
    //implementation("org.springframework.boot:spring-boot-starter-security")                        // filter chain, method security (@PreAuthorize)
    //implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server") // validate JWT bearer tokens, map scopes/roles

    // ---------- Persistence: idempotency keys, payment state machine, outbox, webhook dedup ----------
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")     // schema migrations (unique constraint on idempotency_key, etc.)
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ---------- Resilience: retries w/ backoff, circuit breaker, timeouts, bulkhead, rate limiter ----------
    implementation("io.github.resilience4j:resilience4j-spring-boot4:$resilience4jVersion")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")    // required for @CircuitBreaker/@Retry annotations (replaces starter-aop in Boot 4)

    // ---------- Monitoring / observability ----------
    implementation("org.springframework.boot:spring-boot-starter-actuator")   // health, metrics, circuit-breaker state endpoints
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")               // /actuator/prometheus for Grafana dashboards & alerts
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry") // distributed tracing across outbound calls & webhooks

    // ---------- Dev convenience ----------
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")    // auto-starts Postgres from compose.yaml when running locally

    // ---------- Testing ----------
    testImplementation("org.springframework.boot:spring-boot-starter-test")            // JUnit 5, AssertJ, Mockito, Awaitility-ready
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")     // MockMvc / @WebMvcTest for webhook controller tests
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test") // @RestClientTest, MockRestServiceServer
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")   // @DataJpaTest for idempotency/outbox repositories

    // Mocked external payment API: simulate 500s, timeouts, slow responses, 429s, dropped connections
    testImplementation("org.wiremock.integrations:wiremock-spring-boot:$wiremockSpringBootVersion")

    // Real Postgres in tests (unique-constraint races, transactional behaviour)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    testImplementation("org.awaitility:awaitility")                            // assert async outcomes (webhook processing, polling jobs)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.withType<Test> {
	useJUnitPlatform()
}

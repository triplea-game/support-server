plugins {
    id("java")
    id("io.freefair.lombok") version "8.14.2"
    id("com.diffplug.spotless") version "8.4.0"
    id("io.quarkus") version "3.34.6"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/triplea-game/triplea")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("triplea_github_username") as String?
            password = System.getenv("GH_TOKEN") ?: project.findProperty("triplea_github_access_token") as String?
        }
    }
}

/* "testInteg" runs tests that require a database or a server to be running */
val testInteg: SourceSet = sourceSets.create("testInteg") {
    java {
        java.srcDir("src/testInteg/java")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += output + compileClasspath
    }
}

configurations[testInteg.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[testInteg.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val testIntegTask = tasks.register<Test>("testInteg") {
    group = "verification"
    testClassesDirs = sourceSets["testInteg"].output.classesDirs
    classpath = sourceSets["testInteg"].runtimeClasspath
    // Required for Quarkus @QuarkusTest to use the JBoss log manager
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.check {
    dependsOn(testIntegTask)
}

tasks.clean {
    // Clean Quarkus build artifacts
    delete("build/quarkus-app")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("standardOut", "standardError", "skipped", "failed")
        }
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Duser.timezone=UTC")
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}

val quarkusPlatformVersion = "3.34.6"
val feignVersion = "13.6"
val gsonVersion = "2.12.1"
val junitVersion = "5.13.4"
val mockitoVersion = "5.19.0"
val tripleaVersion = "2.7.15281"

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))

    // Quarkus extensions
    implementation("io.quarkus:quarkus-resteasy-jackson")   // JAX-RS (Classic) + Jackson
    implementation("io.quarkus:quarkus-agroal")             // JDBC connection pool
    implementation("io.quarkus:quarkus-jdbc-postgresql")    // PostgreSQL + Dev Services
    implementation("io.quarkus:quarkus-flyway")             // DB migrations on startup
    implementation("io.quarkus:quarkus-scheduler")          // @Scheduled background tasks
    implementation("org.flywaydb:flyway-database-postgresql")
    // JDBI — framework-agnostic, wires against any DataSource
    implementation("org.jdbi:jdbi3-core:3.53.0")
    implementation("org.jdbi:jdbi3-sqlobject:3.53.0")

    // Gson — used by GithubApiClient
    implementation("com.google.code.gson:gson:$gsonVersion")

    // SnakeYAML Engine — used by MapNameReader to parse map.yml files
    implementation("org.snakeyaml:snakeyaml-engine:2.10")

    // Annotations previously pulled in transitively by DropWizard
    implementation("com.google.guava:guava:33.4.8-jre")           // @VisibleForTesting
    implementation("com.google.code.findbugs:jsr305:3.0.2")      // @Nonnull

    // TripleA shared libraries
    implementation("triplea:domain-data:$tripleaVersion")
    implementation("triplea:java-extras:$tripleaVersion")
    implementation("triplea:lobby-client:$tripleaVersion")
    implementation("triplea:websocket-client:$tripleaVersion")

    // Test dependencies
    testImplementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    testImplementation("io.quarkus:quarkus-junit5")           // @QuarkusTest + Dev Services

    testImplementation("io.github.openfeign:feign-core:$feignVersion")
    testImplementation("io.github.openfeign:feign-gson:$feignVersion")
    testImplementation("triplea:feign-common:$tripleaVersion")

    testImplementation("com.github.database-rider:rider-junit5:1.44.0")
    testImplementation("com.github.npathai:hamcrest-optional:2.0.0")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.wiremock:wiremock:3.13.1")
    testImplementation("ru.lanwen.wiremock:wiremock-junit5:1.3.1")
    testImplementation("uk.co.datumedge:hamcrest-json:0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

plugins {
    id("java")
    id("io.freefair.lombok") version "8.14"
    id("com.diffplug.spotless") version "7.1.0"
    id("com.avast.gradle.docker-compose") version "0.17.12"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("triplea.github.username") as String?
            password = System.getenv("GH_TOKEN") ?: project.findProperty("triplea.github.access.token") as String?
        }
    }
}


tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.triplea.server.SupportServerApplication"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // mergeServiceFiles is needed by dropwizard
    // Without this configuration parsing breaks and is unable to find connector type "http" for
    // the following YAML snippet:  server: {applicationConnectors: [{type: http, port: 8080}]
    mergeServiceFiles()
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
}

tasks.check {
    dependsOn(testIntegTask)
}


///* docker compose used to set up integ tests, starts a server and database */
// See: https://github.com/avast/gradle-docker-compose-plugin
dockerCompose {
    captureContainersOutput = true
    isRequiredBy(testIntegTask)
    setProjectName("support-server")
    // suppress unset variable warning, assign variables to empty string (which will result in random port numbers)
    environment = mapOf("DATABASE_PORT" to "", "SERVER_PORT" to "")
}

tasks.composeBuild {
    dependsOn(tasks.shadowJar)
}

tasks.register<Exec>("dockerComposeClean") {
    group = "docker"
    description = "Docker compose stop and removes volumes"
    commandLine("docker", "compose", "down", "--volumes")
}

tasks.clean {
    dependsOn(tasks.findByName("dockerComposeClean"))
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("standardOut", "standardError", "skipped", "failed")
        }
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}

dependencies {
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("be.tomcools:dropwizard-websocket-jsr356-bundle:4.0.0")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("com.sun.xml.bind:jaxb-core:4.0.5")
    implementation("com.sun.xml.bind:jaxb-impl:4.0.5")
    implementation("io.dropwizard:dropwizard-auth:4.0.7")
    implementation("io.dropwizard:dropwizard-core:4.0.7")
    implementation("io.dropwizard:dropwizard-jdbi3:4.0.7")
    implementation("io.github.openfeign:feign-core:13.6")
    implementation("io.github.openfeign:feign-gson:13.6")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.servlet:servlet-api:2.5")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.apache.httpcomponents:httpclient:4.3.4")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.jdbi:jdbi3-core:3.49.5")
    implementation("org.jdbi:jdbi3-sqlobject:3.49.5")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    implementation("triplea:domain-data:2.7.15062")
    implementation("triplea:feign-common:2.7.15062")
    implementation("triplea:java-extras:2.7.15062")
    implementation("triplea:lobby-client:2.7.15062")
    implementation("triplea:websocket-client:2.7.15062")
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    testImplementation("com.github.database-rider:rider-junit5:1.44.0")
    testImplementation("com.github.npathai:hamcrest-optional:2.0.0")
    testImplementation("com.sun.mail:jakarta.mail:2.0.1")
    testImplementation("io.dropwizard:dropwizard-testing:4.0.7")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.3")

    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
    testImplementation("org.wiremock:wiremock:3.13.1")
    testImplementation("ru.lanwen.wiremock:wiremock-junit5:1.3.1")
    testImplementation("uk.co.datumedge:hamcrest-json:0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.2")
}

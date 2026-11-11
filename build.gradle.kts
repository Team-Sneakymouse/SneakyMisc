import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.serialization") version "2.4.10"
	`maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
	workingDir(rootDir)
	commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
	val (timestamp, hash) = commit.trim().split(":", limit = 2)
	val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
		.format(Instant.ofEpochSecond(timestamp.toLong()))
	"$date-$hash"
}.get()

repositories {
	maven {
		url = uri("https://plugins.gradle.org/m2/")
	}
	maven {
		name = "papermc"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}
	maven {
		url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	}
	maven {
		url = uri("https://maven.enginehub.org/repo/")
	}
	mavenCentral()
	maven {
		url = uri("https://repo.viaversion.com/")
	}
	maven("https://maven.sneakyrp.com/releases")
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
	compileOnly("io.papermc.paper:paper-api:26.2.build.117-stable")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly("com.viaversion:viaversion-api:5.11.0")
	compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:2026.10.09-84db8a554af1") {
		exclude(group = "org.jetbrains.kotlin")
		isTransitive = false
	}
	compileOnly("io.github.team-sneakymouse:magicspells:2026.11.08-eca39854a666") {
		isTransitive = false
	}
	compileOnly("io.github.team-sneakymouse:sneakyitems:2026.11.08-edc6088f8201")
	compileOnly("io.github.team-sneakymouse:SneakyCellPhones:2026.10.10-0059a180a603") {
		isTransitive = false
	}
	compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
		isTransitive = false
	}
	compileOnly("com.sk89q.worldguard:worldguard-core:7.0.18") {
		isTransitive = false
	}
	compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0") {
		isTransitive = false
	}
	compileOnly("com.sk89q.worldedit:worldedit-core:7.4.0") {
		isTransitive = false
	}
	compileOnly("io.github.team-sneakymouse:sneakypocketbase-api:2026.10.09-70effb404392")
	// sneakyutils and sneakyutils-common must share one version: both carry com.sneakyutils.util.ServerUtil.
	compileOnly("io.github.team-sneakymouse:sneakyutils:2026.11.11-eca9fb017f37")
	// Bundled so the Velocity entry point has ServerUtil; proxies have no SneakyUtils plugin.
	implementation("io.github.team-sneakymouse:sneakyutils-common:2026.11.11-eca9fb017f37")
	// Never bundle: a copy of SneakyDBProvider in this JAR would shadow SneakyDB's own registry.
	compileOnly("io.github.team-sneakymouse:sneakydb-api:2026.11.11-6a969d531546")
	compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
	testImplementation(kotlin("test"))
	testImplementation("org.mockito:mockito-core:5.23.0")
	testImplementation("io.papermc.paper:paper-api:26.2.build.117-stable")
	testImplementation("me.clip:placeholderapi:2.11.6")
	testImplementation("io.github.team-sneakymouse:sneakypocketbase-api:2026.10.09-70effb404392")
	testImplementation("io.github.team-sneakymouse:sneakydb-api:2026.11.11-6a969d531546")
	testImplementation("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
	testCompileOnly("io.github.team-sneakymouse:sneakyutils:2026.11.11-eca9fb017f37")
	testRuntimeOnly("io.github.team-sneakymouse:sneakyutils:2026.11.11-eca9fb017f37") {
		isTransitive = false
	}
}

tasks.processResources {
	inputs.property("version", project.version.toString())
	filesMatching(listOf("paper-plugin.yml", "velocity-plugin.json")) {
		expand("version" to project.version.toString())
	}
}

tasks.jar {
	archiveBaseName.set(rootProject.name)
	manifest {
		attributes["Main-Class"] = "com.danidipp.sneakymisc"
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test {
	useJUnitPlatform()
}

configure<JavaPluginExtension> {
	toolchain.languageVersion.set(JavaLanguageVersion.of(25))
	withSourcesJar()
	sourceSets {
		main {
			java.srcDir("src/main/kotlin")
			resources.srcDir(file("src/resources"))
		}
	}
}

val verifyPrivateKotlinRuntime by tasks.registering {
	dependsOn(tasks.jar)
	doLast {
		val jarFile = tasks.jar.get().archiveFile.get().asFile
		val entries = zipTree(jarFile)
		val required = listOf(
			"kotlin/jvm/functions/Function1.class",
			"kotlinx/serialization/json/JsonKt.class"
		)
		required.forEach { path ->
			check(!entries.matching { include(path) }.isEmpty) {
				"$path must be bundled in ${jarFile.name} to keep Kotlin private to this plugin's classloader"
			}
		}
	}
}

// One JAR serves Paper and Velocity: both descriptors and the platform-free ServerUtil must be inside, and
// SneakyDB's API must not be (a bundled SneakyDBProvider would shadow SneakyDB's own registry).
val verifyPlatformJar by tasks.registering {
	dependsOn(tasks.jar)
	doLast {
		val jarFile = tasks.jar.get().archiveFile.get().asFile
		val entries = zipTree(jarFile)
		listOf("paper-plugin.yml", "velocity-plugin.json", "com/sneakyutils/util/ServerUtil.class").forEach { path ->
			check(!entries.matching { include(path) }.isEmpty) { "$path must be in ${jarFile.name}" }
		}
		check(entries.matching { include("com/sneakydb/**") }.isEmpty) { "SneakyDB classes must not be bundled in ${jarFile.name}" }
	}
}

tasks.check {
	dependsOn(verifyPrivateKotlinRuntime, verifyPlatformJar)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			artifactId = rootProject.name
			from(components["java"])
			pom {
				name.set("SneakyMisc")
				description.set("Paper plugin for Lords of Minecraft mechanics that don't justify having a plugin of their own")
				url.set("https://github.com/Team-Sneakymouse/SneakyMisc")
				properties.put("sneakyrp.deployment.type", "paper-plugin,velocity-plugin")
				scm {
					url.set("https://github.com/Team-Sneakymouse/SneakyMisc")
					connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyMisc.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "sneakyrp"
			url = uri("https://maven.sneakyrp.com/releases")
			credentials(PasswordCredentials::class)
			authentication {
				create<org.gradle.authentication.http.BasicAuthentication>("basic")
			}
		}
	}
}

tasks.withType<PublishToMavenRepository>().configureEach {
	dependsOn(tasks.check)
}

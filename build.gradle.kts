plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.serialization") version "2.2.21"
}

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
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	compileOnly("org.apache.logging.log4j:log4j-core:2.24.3")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:1.6.0") {
		exclude(group = "org.jetbrains.kotlin")
	}
	compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
	compileOnly(files("../SneakyPocketbase/build/libs/SneakyPocketbase-1.0-api.jar"))
	compileOnly(fileTree("libs") {
		include("*.jar")
		exclude("SneakyCharacterManager-*.jar")
	})
	testImplementation(kotlin("test"))
	testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	testImplementation("me.clip:placeholderapi:2.11.6")
	testRuntimeOnly(files("../SneakyPocketbase/build/libs/SneakyPocketbase-1.0.jar"))
}

tasks.jar {
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

tasks.check {
	dependsOn(verifyPrivateKotlinRuntime)
}

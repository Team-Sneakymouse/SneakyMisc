plugins {
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.serialization") version "2.4.10"
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
	maven {
		url = uri("https://repo.viaversion.com/")
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
	compileOnly("io.papermc.paper:paper-api:26.2.build.117-stable")
	compileOnly("org.apache.logging.log4j:log4j-core:2.24.3")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly("com.viaversion:viaversion-api:5.11.0")
	compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:1.6.0") {
		exclude(group = "org.jetbrains.kotlin")
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
	compileOnly(files("../SneakyPocketbase/build/libs/SneakyPocketbase-1.0-api.jar"))
	compileOnly(fileTree("libs") {
		include("*.jar")
		exclude("SneakyCharacterManager-*.jar")
	})
	testImplementation(kotlin("test"))
	testImplementation("org.mockito:mockito-core:5.23.0")
	testImplementation("io.papermc.paper:paper-api:26.2.build.117-stable")
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
	toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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

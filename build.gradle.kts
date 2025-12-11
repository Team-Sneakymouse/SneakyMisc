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
	mavenCentral()
}

dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	compileOnly("org.apache.logging.log4j:log4j-core:2.24.3")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly(files("C:\\Users\\DaniDipp\\Downloads\\1.21.4\\SneakyPocketbase-1.0.jar"))
	compileOnly(files("libs/SneakyCharacterManager-1.0-SNAPSHOT.jar"))
	compileOnly(files("libs/MagicSpells-4.0-Beta-13.jar"))
	compileOnly(files("libs/CMI-API9.7.14.3.jar"))
	compileOnly(files("libs/dippgen-1.0.jar"))
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "com.danidipp.sneakymisc"
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

configure<JavaPluginExtension> {
	sourceSets {
		main {
			java.srcDir("src/main/kotlin")
			resources.srcDir(file("src/resources"))
		}
	}
}

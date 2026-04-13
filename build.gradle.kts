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
	compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	compileOnly("org.apache.logging.log4j:log4j-core:2.24.3")
	compileOnly("me.clip:placeholderapi:2.11.6")
	compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
	compileOnly(files("C:\\Users\\DaniDipp\\Downloads\\1.20\\CoreProtect-22.1.jar"))
	compileOnly(files("C:\\Users\\DaniDipp\\Downloads\\1.21.4\\SneakyPocketbase-1.0.jar"))
	compileOnly(fileTree("libs") { include("*.jar") })
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

plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
    kotlin("jvm") version "2.0.21"
}

version = "1.0.0"
group = "com.kenny"

base {
    archivesName.set("antfarm-mod")
}

repositories {
    mavenCentral()
}

dependencies {
    // Minecraft & Fabric - targeting 1.21.4 (compatible with 1.21.x)
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.9")
    
    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.114.0+1.21.4")
    
    // Fabric Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
}

tasks.processResources {
    inputs.property("version", project.version)
    
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}


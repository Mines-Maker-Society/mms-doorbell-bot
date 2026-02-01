plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "edu.mines"
version = "1.0.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pi4j:pi4j-plugin-raspberrypi:3.0.3")
    implementation("com.pi4j:pi4j-plugin-gpiod:3.0.3")
    implementation("com.pi4j:pi4j-core:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.21") 
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("net.dv8tion:JDA:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.51.1.0")
    implementation("com.opencsv:opencsv:5.12.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("edu.mines.mmsbot.MMSApp")
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}
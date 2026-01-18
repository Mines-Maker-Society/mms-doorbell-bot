plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "edu.mines"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("edu.mines.mmsbotupdater.MMSBotUpdater")
}
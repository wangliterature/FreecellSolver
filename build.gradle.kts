plugins {
    id("java")
    id("application")
}

group = "com.solvitaire"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.solvitaire.app.FreeCellStandaloneMain")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

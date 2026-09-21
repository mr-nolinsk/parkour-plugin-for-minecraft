plugins {
    java
}

group = "com.parkourmod"
version = "1.0.0"

// Плагин не тянет сторонних зависимостей внутрь jar (Paper API — compileOnly,
// он уже есть на сервере), поэтому Shadow/fat-jar не нужен: обычный `build`
// уже даёт готовый к установке jar.

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Актуально на сентябрь 2026: сборка Paper именно под MC 1.21.11.
    // Если Paper выпустит новый билд для этой версии, можно смело
    // подставить его номер — API внутри 1.21.11 не поменяется.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

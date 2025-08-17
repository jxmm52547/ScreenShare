plugins {
    id("java")
    id("application")
}

group = "xyz.jxmm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.swinglabs.swingx:swingx-all:1.6.5")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
}

application {
    mainClass.set("xyz.jxmm.screenshare.Application")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// 为邀请码管理工具创建一个单独的运行任务
tasks.register<JavaExec>("runInvitationManager") {
    group = "Application"
    description = "运行邀请码管理工具"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("xyz.jxmm.screenshare.util.InvitationCodeManager")
}
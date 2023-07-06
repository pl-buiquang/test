import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import java.nio.file.Files
import java.io.FileOutputStream
import java.net.URI
import java.nio.channels.Channels
import java.nio.file.Paths

plugins {
    id("java")
    id("com.github.node-gradle.node") version "3.5.1"
}

val rootDir = Paths.get(
    projectDir.absolutePath
)
    .toUri()
    .path
    .substringAfter("/")
    .substringBeforeLast("/")

val currentOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()!!
project.ext.set(
    "os",
    when {
        currentOperatingSystem.isWindows -> "win"
        currentOperatingSystem.isLinux -> "linux"
        currentOperatingSystem.isMacOsX -> "darwin"
        currentOperatingSystem.isSolaris -> ""
        currentOperatingSystem.isFreeBSD -> ""
        else -> ""
    }
)

val currentArchitecture = DefaultNativePlatform.getCurrentArchitecture()!!
project.ext.set(
    "arch",
    when {
        currentArchitecture.isAmd64 -> "x64"
        currentArchitecture.isArm -> "arm64"
        currentArchitecture.isI386 -> ""
        currentArchitecture.isIa64 -> ""
        else -> ""
    }
)

group = "fr.aphp"
version = "0.0.1-SNAPSHOT"

node {
    download.set(true)
    version.set(properties["nodeVersion"] as? String)
}

defaultTasks("cleanIG", "buildIG")

val installSushi = tasks.register<NpmTask>("installSushi") {
    args.set(
        listOf(
            "install",
            "fsh-sushi@${properties["sushiVersion"]}"
        )
    )
    dependsOn(
        tasks.nodeSetup,
        tasks.npmSetup,
        tasks.npmInstall
    )
}

val runSushi = tasks.register<NpxTask>("runSushi") {
    command.set("sushi")
    args.set(
        listOf(".")
    )
    dependsOn(
        tasks.nodeSetup,
        tasks.npmSetup,
        tasks.npmInstall,
        installSushi
    )
}

val installIGPublisher = tasks.register<Task>("installIGPublisher") {
    group = "build setup"
    doLast {
        val inputCacheDir = file("input-cache").toPath()
        val publisherJar = file("input-cache/publisher.jar").toPath()

        if (!Files.exists(inputCacheDir)) {
            Files.createDirectory(inputCacheDir)
        }

        if (!Files.exists(publisherJar)) {
            URI.create("https://github.com/HL7/fhir-ig-publisher/releases/download/${properties["publisherVersion"]}/publisher.jar")
                .toURL().openStream().use {
                    Channels.newChannel(it).use { rbc ->
                        FileOutputStream("input-cache/publisher.jar").use { fos ->
                            fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                        }
                    }
                }
        }
    }
    dependsOn(
        tasks.nodeSetup,
        tasks.npmSetup,
        tasks.npmInstall,
        installSushi
    )
}

val buildIG = tasks.register<JavaExec>("buildIG") {
    group = "build"
    environment(
        "PATH",
        listOf(
            System.getenv("PATH"),
            "$rootDir/.gradle/nodejs/node-v${properties["nodeVersion"]}-"+"${project.ext.get("os")}-${project.ext.get("arch")}",
            "$rootDir/node_modules/.bin"
        ).joinToString(
            System.getProperties().getProperty("path.separator")
        )
    )
    if (System.getenv("HOST_PROXY") != null && System.getenv("PORT_PROXY") != null) {
        val proxy_host = System.getenv("HOST_PROXY")
        val proxy_port = System.getenv("PORT_PROXY")
        jvmArgs("-Dhttp.proxyHost=${proxy_host}", "-Dhttp.proxyPort=${proxy_port}", "-Dhttps.proxyHost=${proxy_host}", "-Dhttps.proxyPort=${proxy_port}")
    }
    jvmArgs("-Dfile.encoding=UTF-8")
    classpath("input-cache/publisher.jar")
    args = listOf("-ig", ".")
    dependsOn(
        tasks.nodeSetup,
        tasks.npmSetup,
        tasks.npmInstall,
        installSushi,
        installIGPublisher
    )
}

val cleanIG = tasks.register<Delete>("cleanIG") {
    group = "build"
    delete(
        "fsh-generated",
        "output",
        "temp",
        "template",
        "input-cache/schemas",
        "input-cache/txcache",
        "input-cache/publisher.jar"
    )
}
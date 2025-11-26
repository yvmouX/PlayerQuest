plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.0-rc3"
}

allprojects {
    apply(plugin = "java")

    group = "com.playerPlugin"
    version = "1.0.0"

    // Java 版本配置
    val targetJavaVersion = 21
    java {
        val javaVersion = JavaVersion.toVersion(targetJavaVersion)
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion

        // 若当前 JDK 版本低于目标版本，使用 Toolchain 指定 JDK 版本
        if (JavaVersion.current() < javaVersion) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        }
    }

    // Java 编译任务配置
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"

        // 针对 Java 10+ 启用 --release 参数
        if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
            options.release.set(targetJavaVersion)
        }
    }

    repositories {
        mavenCentral()
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://jitpack.io")}
        maven { url = uri("https://repo.tcoded.com/releases")}
        maven { url = uri("https://repo.rosewooddev.io/repository/public/")}
    }

    dependencies {
        //implementation("com.github.yvmouX:YLib:1.0.0-beta4")
        implementation(files(rootProject.file("lib/YLib-1.0.0-beta5.jar")))
    }
}

project(":core") {
    dependencies {
        compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
        implementation("me.devnatan:inventory-framework-platform-paper:3.5.5")
        implementation("me.devnatan:inventory-framework-platform-bukkit:3.5.5")
        compileOnly("com.github.MilkBowl:VaultAPI:1.7")
        implementation("org.black_ixx:playerpoints:3.3.4-SNAPSHOT")

        compileOnly("org.xerial:sqlite-jdbc:3.42.0.0")
        implementation("mysql:mysql-connector-java:8.0.33")
        compileOnly("com.googlecode.json-simple:json-simple:1.1.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    }
}

project(":api") {
    implementation(project(":core"))
}



tasks {
    runServer {
        minecraftVersion("1.21")
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
    }
}

tasks.shadowJar {
    relocate("cn.yvmou.ylib", "com.playerPlugin.playerTaskX.lib.ylib")
    relocate("me.devnatan.inventoryframework", "com.playerPlugin.playerTaskX.lib.inventoryframework")

    // 排除依赖中的 plugin.yml （避免冲突）
    exclude { file ->
        file.name == "plugin.yml" &&
                (file.path.contains("me/devnatan/inventoryframework") ||
                        file.path.contains("inventory-framework"))
    }

    // 强制保留自己的 plugin.yml（双重保险）
    from("src/main/resources/plugin.yml") {
        into("/") // 放入 JAR 根目录
    }

    // 重复文件处理策略：排除重复
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 输出文件名格式：项目名-版本号-all.jar
    archiveFileName.set("${project.name}-${project.version}-all.jar")
}


// 资源处理任务配置（替换 plugin.yml 中的版本变量）
tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"

    // 对 plugin.yml 进行变量替换（例如 ${version} 替换为项目版本）
    filesMatching("plugin.yml") {
        expand(props)
    }
}
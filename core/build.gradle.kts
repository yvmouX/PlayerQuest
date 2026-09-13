dependencies {
    // 依赖 api 模块
    implementation(project(":api"))

    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.black_ixx:playerpoints:3.3.4-SNAPSHOT")

    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    compileOnly("mysql:mysql-connector-java:8.0.33")
    implementation("com.zaxxer:HikariCP:4.0.3")


    // 网页编辑器只需要「路由 + 静态资源 + 文本响应」，Javalin 核心足够；
    // openapi/swagger/redoc 三个插件从未注册过，属于历史遗留的依赖，已删除
    implementation("io.javalin:javalin:6.7.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    // spigot-api 在主代码里是 compileOnly（运行期由服务端提供），
    // 但测试编译与运行都需要它的类（例如 ChatColor 的颜色码转换），因此单独给测试加一份
    testImplementation("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// 定义前端构建任务
val frontendBuild by tasks.registering {
    description = "Build frontend assets"
    group = "build"

    doLast {
        val frontendDir = rootProject.projectDir.resolve("task-editor-vue")

        // 运行 npm build (Windows 需要 npm.cmd)
        val npmCmd = if (System.getProperty("os.name").contains("Windows")) "npm.cmd" else "npm"
        project.rootProject.exec {
            workingDir(frontendDir)
            commandLine(npmCmd, "run", "build")
        }

        // 这里曾经还有一段「若 task-editor-vue/dist 存在就覆盖 src/main/resources/web」。
        // 但 vite 的 outDir 早已直接指向 web（见 task-editor-vue/vite.config.ts），dist 不会再出现，
        // 那段是死代码；更糟的是一旦 dist 因任何原因重新出现，它就会用旧产物覆盖掉刚构建好的 web。
        // 已删除——产物的归属只有一个地方：vite 自己。
    }
}

// 确保在 processResources 之前完成前端构建
tasks.processResources {
    dependsOn(frontendBuild)

    // plugin.yml 的 version 用 ${version} 占位，打包时替换成项目版本，避免两处各写一份而漂移。
    // 注意这段必须配在 core 上：plugin.yml 位于 core/src/main/resources，
    // 配在 root 项目上的话 root 没有 src 目录，:processResources 是 NO-SOURCE，替换根本不会发生。
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
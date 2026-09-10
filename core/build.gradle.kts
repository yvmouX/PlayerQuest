dependencies {
    // 依赖 api 模块
    implementation(project(":api"))

    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.black_ixx:playerpoints:3.3.4-SNAPSHOT")

    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    compileOnly("mysql:mysql-connector-java:8.0.33")
    implementation("com.zaxxer:HikariCP:4.0.3")


    implementation("com.alibaba.fastjson2:fastjson2:2.0.60")

    implementation("io.javalin:javalin:6.7.0")
    val openapi = "6.7.0"
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$openapi")
    // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:${openapi}")
    // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:${openapi}")
    // for ReDoc UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:${openapi}")

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
        val webDir = projectDir.resolve("src/main/resources/web")
        
        // 运行 npm build (Windows 需要 npm.cmd)
        val npmCmd = if (System.getProperty("os.name").contains("Windows")) "npm.cmd" else "npm"
        project.rootProject.exec {
            workingDir(frontendDir)
            commandLine(npmCmd, "run", "build")
        }
        
        // 复制构建产物到 web 目录
        val distDir = frontendDir.resolve("dist")
        if (distDir.exists()) {
            webDir.deleteRecursively()
            distDir.copyRecursively(webDir)
            println("Frontend build copied to $webDir")
        }
    }
}

// 确保在 processResources 之前完成前端构建
tasks.processResources {
    dependsOn(frontendBuild)
}
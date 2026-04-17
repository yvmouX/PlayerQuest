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


}

val copyFrontendBuild by tasks.registering(Copy::class) {
    from("${project.rootDir}/task-editor-vue/dist")
    into("${projectDir}/src/main/resources/web")
}

assemble {
    dependsOn(copyFrontendBuild)
}
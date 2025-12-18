rootProject.name = "playerTaskX"

include(":api")
include(":core")
includeBuild("YLib") {
    dependencySubstitution {
        substitute(module("com.github.yvmouX:YLib")).using(project(":"))
    }
}
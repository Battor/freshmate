pluginManagement {
    repositories {
        // F-Droid 可复现构建要求依赖仅来自可信仓库（google/mavenCentral 等），
        // 不加镜像源；foojay-resolver（构建期自动下载 JDK）也被其扫描器禁止
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FreshMate"
include(":app")

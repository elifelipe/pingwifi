pluginManagement {
    repositories {
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
        // CORREÇÃO: A sintaxe para o repositório JitPack foi corrigida.
        // Esta linha é necessária para as bibliotecas dotlottie-android e AndroidNetworkTools.
        maven { url = uri("https://jitpack.io") }
        // Repositório para traceroute-for-android
        maven { url = uri("https://artifactory.appodeal.com/appodeal-public") }
    }
}

rootProject.name = "Ping Wifi"
include(":app")

plugins {
    id("com.android.application")
}

android {
    namespace = "com.haval.akppmodes"
    compileSdk = 28

    defaultConfig {
        applicationId = "com.haval.akppmodes"
        minSdk = 28
        targetSdk = 28
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            // Включаем обфускацию и минификацию кода
            isMinifyEnabled = true
            
            // Включаем удаление неиспользуемых ресурсов для уменьшения размера APK
            isShrinkResources = true
            
            // Подключаем стандартные правила оптимизации Google + ваш личный файл правил
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // No external deps — pure system APIs + reflection for Car
}

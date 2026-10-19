import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Clé de signature de release, décrite dans `keystore.properties` à la racine du projet.
 *
 * Ce fichier et le keystore restent hors du dépôt : sans eux la compilation reste possible et
 * produit simplement un APK de release non signé.
 */
val proprietesKeystore = Properties().apply {
    val fichier = rootProject.file("keystore.properties")
    if (fichier.exists()) fichier.inputStream().use { load(it) }
}

// On vérifie le `.jks` lui-même et pas seulement la présence de la propriété : le fichier de
// propriétés peut rester en place alors que le keystore a été déplacé, et `assembleRelease`
// échouerait sur « Keystore file not found » au lieu de sortir l'APK non signé.
val fichierKeystore: File? = proprietesKeystore.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }

android {
    namespace = "com.music36.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.music36.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "5.3"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (fichierKeystore != null) {
            create("release") {
                storeFile = fichierKeystore
                storePassword = proprietesKeystore.getProperty("storePassword")
                keyAlias = proprietesKeystore.getProperty("keyAlias")
                keyPassword = proprietesKeystore.getProperty("keyPassword")
                // minSdk 26 : v1 (signature JAR) ne sert plus à rien, v2 et v3 suffisent — et
                // v3 est ce qui permettra, le jour venu, de changer de clé sans réinstaller.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // `BuildConfig.VERSION_CODE` est lu par les tests : c'est lui qui garantit qu'une version
        // ne peut pas être livrée sans son entrée au journal des nouveautés.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Lecture audio : ExoPlayer pour le décodage, media3-session pour la notification,
    // l'écran verrouillé, les commandes du casque et Android Auto.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    // Robolectric compose réellement l'écran : c'est le seul filet, sans téléphone, contre une
    // interface qui compile mais s'effondre au premier affichage.
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

package com.music36.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.magasin: DataStore<Preferences> by preferencesDataStore(name = "reglages")

/**
 * Tous les réglages de l'application, en un seul objet immuable.
 *
 * Le passer d'un bloc à l'autre plutôt que d'interroger DataStore à la demande évite les états
 * intermédiaires : l'écran, le service de lecture et l'analyseur voient toujours la même chose.
 */
data class EtatReglages(
    /**
     * Le `versionCode` dont les nouveautés ont déjà été annoncées.
     *
     * Zéro signifie « jamais annoncé » — soit une installation neuve, soit une mise à jour venue
     * d'une version antérieure à cette mécanique.
     */
    val derniereVersionVue: Int = 0,
    /** Ce qui s'affiche derrière la pochette, vue molette : l'ordinal de [FondLecteur]. */
    val fondLecteurRetro: Int = 0,
    /** La teinte retenue quand le fond est une couleur unie : un index dans [COULEURS_FOND]. */
    val couleurFondRetro: Int = 0,
    /** L'allure de la molette : l'ordinal de [StyleMolette]. */
    val styleMolette: Int = 0,
    /** Sa teinte : un index dans [COULEURS_MOLETTE], zéro suivant le thème. */
    val couleurMolette: Int = 0,
    /** Ce qui suit le doigt : l'ordinal de [EffetMolette]. */
    val effetMolette: Int = 0,
    /**
     * L'apparence propre à l'interface molette.
     *
     * ⚠️ Séparée de celle de l'interface classique : les deux ne se ressemblent en rien, et
     * ce qui va à l'une — un thème clair pour les listes — dessert souvent l'autre.
     */
    val themeRetro: Int = ThemeApplication.SOMBRE.ordinal,
    /** L'ambiance de couleurs de la vue molette : l'ordinal de [ThemeCouleur]. */
    val themeCouleurRetro: Int = ThemeCouleur.CLASSIQUE.ordinal,
    /** La teinte choisie dans cette ambiance, en degrés sur le cercle chromatique. */
    val teinteRetro: Float = ThemeCouleur.CLASSIQUE.teinteParDefaut,
    val dynamiqueRetro: Boolean = false,
    /** Le liseré de couleur sur le cadre, la molette et les boutons de l'écran de lecture. */
    val lisereLecteur: Boolean = true,
    /** L'allure de la barre de progression : l'ordinal de [StyleBarre]. */
    val styleBarre: Int = 0,
    /**
     * Les apparences mises de côté, une chaîne par emplacement (vide = emplacement libre).
     *
     * Stockées en texte plutôt qu'en une clé par valeur : trois emplacements de onze réglages
     * feraient trente-trois clés, et un réglage d'apparence ajouté plus tard obligerait à les
     * multiplier. Voir [ConfigurationApparence].
     */
    val configurationsRetro: List<String> = List(NOMBRE_CONFIGURATIONS) { "" },
    /**
     * Les dossiers où l'application a exporté des playlists, séparés par des retours à la ligne.
     *
     * ⚠️ L'analyse **n'y importe aucune playlist**. Sans cela, exporter dans un dossier déjà
     * surveillé fait réapparaître chaque playlist en double à l'analyse suivante : l'originale,
     * et la copie que l'on vient d'écrire. Un dossier qui est lui-même surveillé n'entre jamais
     * dans cette liste — l'utilisateur a alors demandé explicitement qu'on le lise.
     */
    val dossiersExportes: String = "",
    /** Le tri de chaque playlist, une ligne `id:ordinal` par playlist triée. */
    val trisPlaylists: String = "",
    // --- Bibliothèque ---
    val analyseAuDemarrage: Boolean = true,
    val dureeMinimale: Int = 0,
    val ignorerFichiersCaches: Boolean = true,
    val analyseApprofondie: Boolean = true,
    val importerM3u: Boolean = true,
    /**
     * Réécrire le `.m3u` d'origine quand on modifie une playlist importée.
     *
     * Sans cela, le fichier et l'application divergent en silence : le titre retiré ici
     * reste là-bas, et reviendra le jour où le fichier changera pour une autre raison.
     */
    val repercuterM3u: Boolean = true,
    // --- Lecture ---
    val fonduMs: Int = 0,
    val fonduPause: Boolean = true,
    val sauterSilences: Boolean = false,
    val vitesse: Float = 1f,
    val tonaliteSuitVitesse: Boolean = false,
    val reprendreAuDemarrage: Boolean = true,
    val memoriserPositionLongues: Boolean = true,
    // --- Sortie audio ---
    val sortieFlottante: Boolean = true,
    val dechargementAudio: Boolean = false,
    val routageUsb: Boolean = true,
    // --- Égaliseur ---
    val egaliseurActif: Boolean = false,
    val egaliseurPreset: Int = -1,
    val egaliseurBandes: String = "",
    val bassBoost: Int = 0,
    val virtualiseur: Int = 0,
    val gainSonore: Int = 0,
    // --- Interface ---
    val theme: ThemeApplication = ThemeApplication.SYSTEME,
    val palette: PaletteApplication = PaletteApplication.CLASSIQUE,
    /**
     * L'interface montrée au démarrage.
     *
     * ⚠️ **La molette est l'interface principale depuis la 4.3.** L'interface moderne reste
     * entière, mais on y va depuis la molette (`Réglages → Interface moderne`) pour y lire une
     * liste ou un lecteur plein écran — plus pour régler quoi que ce soit.
     */
    val style: StyleInterface = StyleInterface.RETRO,
    val couleursDynamiques: Boolean = true,
    val fondPochette: Boolean = true,
    val ongletDefaut: Onglet = Onglet.SONS,
    val afficherQualite: Boolean = true,
    val ecranAllume: Boolean = false,
    val tri: Tri = Tri.ALPHABETIQUE,
    val triCroissant: Boolean = true,
    // --- Reprise de session ---
    val fileMemorisee: String = "",
    val indexMemorise: Int = 0,
    val positionMemorisee: Long = 0,
    val aleatoireMemorise: Boolean = false,
    val repetitionMemorisee: Int = 0
) {
    /** Les bandes de l'égaliseur, en millibels, telles qu'enregistrées. */
    val bandes: List<Int>
        get() = egaliseurBandes.split(',').mapNotNull { it.trim().toIntOrNull() }
}

class Reglages(private val contexte: Context) {

    val flux: Flow<EtatReglages> = contexte.magasin.data.map { p -> lire(p) }

    private fun lire(p: Preferences) = EtatReglages(
        derniereVersionVue = p[Cles.derniereVersionVue] ?: 0,
        fondLecteurRetro = p[Cles.fondLecteurRetro] ?: 0,
        couleurFondRetro = p[Cles.couleurFondRetro] ?: 0,
        styleMolette = p[Cles.styleMolette] ?: 0,
        couleurMolette = p[Cles.couleurMolette] ?: 0,
        effetMolette = p[Cles.effetMolette] ?: 0,
        themeRetro = p[Cles.themeRetro] ?: ThemeApplication.SOMBRE.ordinal,
        themeCouleurRetro = p[Cles.themeCouleurRetro] ?: ThemeCouleur.depuisPaletteHeritee(0).ordinal,
        teinteRetro = p[Cles.teinteRetro] ?: ThemeCouleur.teintePaletteHeritee(0),
        dynamiqueRetro = p[Cles.dynamiqueRetro] ?: false,
        lisereLecteur = p[Cles.lisereLecteur] ?: true,
        styleBarre = p[Cles.styleBarre] ?: 0,
        configurationsRetro = Cles.configurationsRetro.map { p[it] ?: "" },
        dossiersExportes = p[Cles.dossiersExportes] ?: "",
        trisPlaylists = p[Cles.trisPlaylists] ?: "",
        analyseAuDemarrage = p[Cles.analyseAuDemarrage] ?: true,
        dureeMinimale = p[Cles.dureeMinimale] ?: 0,
        ignorerFichiersCaches = p[Cles.ignorerFichiersCaches] ?: true,
        analyseApprofondie = p[Cles.analyseApprofondie] ?: true,
        importerM3u = p[Cles.importerM3u] ?: true,
        repercuterM3u = p[Cles.repercuterM3u] ?: true,
        fonduMs = p[Cles.fonduMs] ?: 0,
        fonduPause = p[Cles.fonduPause] ?: true,
        sauterSilences = p[Cles.sauterSilences] ?: false,
        vitesse = p[Cles.vitesse] ?: 1f,
        tonaliteSuitVitesse = p[Cles.tonaliteSuitVitesse] ?: false,
        reprendreAuDemarrage = p[Cles.reprendreAuDemarrage] ?: true,
        memoriserPositionLongues = p[Cles.memoriserPositionLongues] ?: true,
        sortieFlottante = p[Cles.sortieFlottante] ?: true,
        dechargementAudio = p[Cles.dechargementAudio] ?: false,
        routageUsb = p[Cles.routageUsb] ?: true,
        egaliseurActif = p[Cles.egaliseurActif] ?: false,
        egaliseurPreset = p[Cles.egaliseurPreset] ?: -1,
        egaliseurBandes = p[Cles.egaliseurBandes] ?: "",
        bassBoost = p[Cles.bassBoost] ?: 0,
        virtualiseur = p[Cles.virtualiseur] ?: 0,
        gainSonore = p[Cles.gainSonore] ?: 0,
        theme = p[Cles.theme]?.let { runCatching { ThemeApplication.valueOf(it) }.getOrNull() }
            ?: ThemeApplication.SYSTEME,
        palette = p[Cles.palette]?.let { runCatching { PaletteApplication.valueOf(it) }.getOrNull() }
            ?: PaletteApplication.CLASSIQUE,
        style = p[Cles.style]?.let { runCatching { StyleInterface.valueOf(it) }.getOrNull() }
            ?: StyleInterface.RETRO,
        couleursDynamiques = p[Cles.couleursDynamiques] ?: true,
        fondPochette = p[Cles.fondPochette] ?: true,
        ongletDefaut = p[Cles.ongletDefaut]?.let { runCatching { Onglet.valueOf(it) }.getOrNull() }
            ?: Onglet.SONS,
        afficherQualite = p[Cles.afficherQualite] ?: true,
        ecranAllume = p[Cles.ecranAllume] ?: false,
        tri = p[Cles.tri]?.let { runCatching { Tri.valueOf(it) }.getOrNull() } ?: Tri.ALPHABETIQUE,
        triCroissant = p[Cles.triCroissant] ?: true,
        fileMemorisee = p[Cles.fileMemorisee] ?: "",
        indexMemorise = p[Cles.indexMemorise] ?: 0,
        positionMemorisee = p[Cles.positionMemorisee] ?: 0L,
        aleatoireMemorise = p[Cles.aleatoireMemorise] ?: false,
        repetitionMemorisee = p[Cles.repetitionMemorisee] ?: 0
    )

    suspend fun modifier(bloc: (EtatReglages) -> EtatReglages) {
        contexte.magasin.edit { p ->
            val nouveau = bloc(lire(p))
            p[Cles.derniereVersionVue] = nouveau.derniereVersionVue
            p[Cles.fondLecteurRetro] = nouveau.fondLecteurRetro
            p[Cles.couleurFondRetro] = nouveau.couleurFondRetro
            p[Cles.styleMolette] = nouveau.styleMolette
            p[Cles.couleurMolette] = nouveau.couleurMolette
            p[Cles.effetMolette] = nouveau.effetMolette
            p[Cles.themeRetro] = nouveau.themeRetro
            p[Cles.themeCouleurRetro] = nouveau.themeCouleurRetro
            p[Cles.teinteRetro] = nouveau.teinteRetro
            p[Cles.dynamiqueRetro] = nouveau.dynamiqueRetro
            p[Cles.lisereLecteur] = nouveau.lisereLecteur
            p[Cles.styleBarre] = nouveau.styleBarre
            Cles.configurationsRetro.forEachIndexed { index, cle ->
                p[cle] = nouveau.configurationsRetro.getOrElse(index) { "" }
            }
            p[Cles.dossiersExportes] = nouveau.dossiersExportes
            p[Cles.trisPlaylists] = nouveau.trisPlaylists
            p[Cles.analyseAuDemarrage] = nouveau.analyseAuDemarrage
            p[Cles.dureeMinimale] = nouveau.dureeMinimale
            p[Cles.ignorerFichiersCaches] = nouveau.ignorerFichiersCaches
            p[Cles.analyseApprofondie] = nouveau.analyseApprofondie
            p[Cles.importerM3u] = nouveau.importerM3u
            p[Cles.repercuterM3u] = nouveau.repercuterM3u
            p[Cles.fonduMs] = nouveau.fonduMs
            p[Cles.fonduPause] = nouveau.fonduPause
            p[Cles.sauterSilences] = nouveau.sauterSilences
            p[Cles.vitesse] = nouveau.vitesse
            p[Cles.tonaliteSuitVitesse] = nouveau.tonaliteSuitVitesse
            p[Cles.reprendreAuDemarrage] = nouveau.reprendreAuDemarrage
            p[Cles.memoriserPositionLongues] = nouveau.memoriserPositionLongues
            p[Cles.sortieFlottante] = nouveau.sortieFlottante
            p[Cles.dechargementAudio] = nouveau.dechargementAudio
            p[Cles.routageUsb] = nouveau.routageUsb
            p[Cles.egaliseurActif] = nouveau.egaliseurActif
            p[Cles.egaliseurPreset] = nouveau.egaliseurPreset
            p[Cles.egaliseurBandes] = nouveau.egaliseurBandes
            p[Cles.bassBoost] = nouveau.bassBoost
            p[Cles.virtualiseur] = nouveau.virtualiseur
            p[Cles.gainSonore] = nouveau.gainSonore
            p[Cles.theme] = nouveau.theme.name
            p[Cles.palette] = nouveau.palette.name
            p[Cles.style] = nouveau.style.name
            p[Cles.couleursDynamiques] = nouveau.couleursDynamiques
            p[Cles.fondPochette] = nouveau.fondPochette
            p[Cles.ongletDefaut] = nouveau.ongletDefaut.name
            p[Cles.afficherQualite] = nouveau.afficherQualite
            p[Cles.ecranAllume] = nouveau.ecranAllume
            p[Cles.tri] = nouveau.tri.name
            p[Cles.triCroissant] = nouveau.triCroissant
            p[Cles.fileMemorisee] = nouveau.fileMemorisee
            p[Cles.indexMemorise] = nouveau.indexMemorise
            p[Cles.positionMemorisee] = nouveau.positionMemorisee
            p[Cles.aleatoireMemorise] = nouveau.aleatoireMemorise
            p[Cles.repetitionMemorisee] = nouveau.repetitionMemorisee
        }
    }

    private object Cles {
        val derniereVersionVue = intPreferencesKey("derniere_version_vue")
        val fondLecteurRetro = intPreferencesKey("fond_lecteur_retro")
        val couleurFondRetro = intPreferencesKey("couleur_fond_retro")
        val styleMolette = intPreferencesKey("style_molette")
        val couleurMolette = intPreferencesKey("couleur_molette")
        val effetMolette = intPreferencesKey("effet_molette")
        val themeRetro = intPreferencesKey("theme_retro")
        val themeCouleurRetro = intPreferencesKey("theme_couleur_retro")
        val teinteRetro = floatPreferencesKey("teinte_retro")
        val dynamiqueRetro = booleanPreferencesKey("dynamique_retro")
        val lisereLecteur = booleanPreferencesKey("lisere_lecteur")
        val styleBarre = intPreferencesKey("style_barre")
        val analyseAuDemarrage = booleanPreferencesKey("analyse_demarrage")
        val dureeMinimale = intPreferencesKey("duree_minimale")
        val ignorerFichiersCaches = booleanPreferencesKey("ignorer_caches")
        val analyseApprofondie = booleanPreferencesKey("analyse_approfondie")
        val importerM3u = booleanPreferencesKey("importer_m3u")
        val repercuterM3u = booleanPreferencesKey("repercuter_m3u")
        val fonduMs = intPreferencesKey("fondu_ms")
        val fonduPause = booleanPreferencesKey("fondu_pause")
        val sauterSilences = booleanPreferencesKey("sauter_silences")
        val vitesse = floatPreferencesKey("vitesse")
        val tonaliteSuitVitesse = booleanPreferencesKey("tonalite_suit_vitesse")
        val reprendreAuDemarrage = booleanPreferencesKey("reprendre_demarrage")
        val memoriserPositionLongues = booleanPreferencesKey("memoriser_position")
        val sortieFlottante = booleanPreferencesKey("sortie_flottante")
        val dechargementAudio = booleanPreferencesKey("dechargement_audio")
        val routageUsb = booleanPreferencesKey("routage_usb")
        val egaliseurActif = booleanPreferencesKey("egaliseur_actif")
        val egaliseurPreset = intPreferencesKey("egaliseur_preset")
        val egaliseurBandes = stringPreferencesKey("egaliseur_bandes")
        val bassBoost = intPreferencesKey("bass_boost")
        val virtualiseur = intPreferencesKey("virtualiseur")
        val gainSonore = intPreferencesKey("gain_sonore")
        val theme = stringPreferencesKey("theme")
        val palette = stringPreferencesKey("palette")
        val style = stringPreferencesKey("style_interface")
        val couleursDynamiques = booleanPreferencesKey("couleurs_dynamiques")
        val fondPochette = booleanPreferencesKey("fond_pochette")
        val ongletDefaut = stringPreferencesKey("onglet_defaut")
        val afficherQualite = booleanPreferencesKey("afficher_qualite")
        val ecranAllume = booleanPreferencesKey("ecran_allume")
        val tri = stringPreferencesKey("tri")
        val triCroissant = booleanPreferencesKey("tri_croissant")
        val fileMemorisee = stringPreferencesKey("file_memorisee")
        val indexMemorise = intPreferencesKey("index_memorise")

        val positionMemorisee = longPreferencesKey("position_memorisee")
        val aleatoireMemorise = booleanPreferencesKey("aleatoire_memorise")
        val repetitionMemorisee = intPreferencesKey("repetition_memorisee")
        val configurationsRetro = List(NOMBRE_CONFIGURATIONS) { index ->
            stringPreferencesKey("configuration_retro_${index + 1}")
        }
        val dossiersExportes = stringPreferencesKey("dossiers_exportes")
        val trisPlaylists = stringPreferencesKey("tris_playlists")
    }
}

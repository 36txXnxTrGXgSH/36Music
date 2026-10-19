package com.music36.app.data

/** La nature d'une ligne de nouveauté : elle décide de sa puce et de sa couleur. */
enum class GenreNouveaute(val libelle: String) {
    AJOUT("Nouveau"),
    CORRECTION("Corrigé"),
    AMELIORATION("Amélioré")
}

/** Une ligne du journal des versions. */
data class LigneNouveaute(val genre: GenreNouveaute, val texte: String)

/** Ce qu'une version a apporté. */
data class Nouveaute(
    val code: Int,
    val nom: String,
    val date: String,
    val lignes: List<LigneNouveaute>
)

/**
 * Le journal des versions, tel que l'application le montre au premier démarrage après une
 * mise à jour.
 *
 * ⚠️ **Chaque nouvelle version ajoute son entrée ici, en tête.** Une version livrée sans son
 * entrée passe inaperçue : l'écran de nouveautés ne peut annoncer que ce qui y est écrit.
 *
 * Les lignes s'adressent à quelqu'un qui se sert de l'application, pas à quelqu'un qui la lit :
 * on décrit ce qui change à l'usage, pas le nom des classes touchées.
 */
object Nouveautes {

    private fun ajout(texte: String) = LigneNouveaute(GenreNouveaute.AJOUT, texte)
    private fun correction(texte: String) = LigneNouveaute(GenreNouveaute.CORRECTION, texte)
    private fun amelioration(texte: String) = LigneNouveaute(GenreNouveaute.AMELIORATION, texte)

    val journal: List<Nouveaute> = listOf(
        Nouveaute(
            code = 45, nom = "5.3", date = "octobre 2026",
            lignes = listOf(
                amelioration(
                    "L'interface à molette s'appelle désormais la « vue Rétro », et son menu de " +
                        "réglages « Réglages de la vue Rétro ». Rien d'autre ne change : vos " +
                        "couleurs, votre thème et vos configurations enregistrées sont repris."
                ),
                amelioration(
                    "Première version publique du code source."
                )
            )
        ),
        Nouveaute(
            code = 44, nom = "5.2", date = "octobre 2026",
            lignes = listOf(
                correction(
                    "La molette ne traîne plus dans les résultats d'une recherche. Chaque cran " +
                        "refaisait toute la recherche, et reconstruisait et retriait la playlist " +
                        "entière ; les deux sont maintenant gardés tant que rien ne change."
                ),
                ajout(
                    "Dans une playlist, l'appui long sur un titre propose « Retirer de cette " +
                        "playlist ». Si le titre y figure plusieurs fois, seule la ligne choisie " +
                        "est retirée, même quand la playlist est triée. Le fichier .m3u suit."
                )
            )
        ),
        Nouveaute(
            code = 43, nom = "5.1", date = "octobre 2026",
            lignes = listOf(
                ajout(
                    "Recherche dans tous les menus de la molette, sauf « Lecture en cours » : " +
                        "la loupe à droite du titre ouvre le clavier et filtre le menu à mesure " +
                        "qu'on tape. La molette reprend ensuite la main pour choisir."
                ),
                ajout(
                    "Jokers : « emi* » commence par emi, « *emi » finit par emi, « rat*emi » " +
                        "commence par rat et finit par emi. Sans étoile, le nom contient le mot. " +
                        "Majuscules et accents sont ignorés, et l'artiste est cherché aussi."
                ),
                ajout(
                    "Dans chaque playlist, un bouton « Trier » change directement l'ordre : " +
                        "titre, artiste, date d'ajout, puis l'ordre du fichier. Le tri est " +
                        "retenu pour chaque playlist, et la lecture suit l'ordre affiché."
                ),
                amelioration(
                    "MENU ferme d'abord la recherche avant de remonter d'un niveau."
                )
            )
        ),
        Nouveaute(
            code = 42, nom = "5.0", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Les playlists en double disparaissent vraiment. Un même fichier atteint " +
                        "par deux dossiers surveillés imbriqués donnait deux adresses " +
                        "différentes, et l'application y voyait deux playlists. Elles sont " +
                        "maintenant reconnues par le fichier lui-même."
                ),
                amelioration(
                    "L'analyse ne lit plus deux fois le même fichier de playlist, même s'il se " +
                        "trouve dans deux dossiers surveillés imbriqués l'un dans l'autre."
                )
            )
        ),
        Nouveaute(
            code = 41, nom = "4.9", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Les playlists en double disparaissent. Deux lignes s'étaient retrouvées " +
                        "sur le même fichier : renommer « X.m3u » en « X.m3u8 » alors qu'une " +
                        "copie exportée portait déjà ce nom faisait tomber les deux au même " +
                        "endroit. Le ménage garde la plus fournie — et met d'abord son fichier " +
                        "à jour, pour que rien de l'application ne soit perdu."
                ),
                ajout(
                    "La liste des playlists suit maintenant les fichiers présents sur " +
                        "l'appareil : ce qui n'existe plus n'y figure plus. Le ménage a lieu " +
                        "après chaque analyse, et à la demande par Paramètres → Playlists → " +
                        "« Retirer les doublons »."
                ),
                correction(
                    "Une playlist n'est plus renommée en .m3u8 si un fichier de ce nom existe " +
                        "déjà à côté : c'est ce qui avait fait fusionner deux playlists."
                )
            )
        ),
        Nouveaute(
            code = 40, nom = "4.8", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Exporter vos playlists ne les fait plus apparaître en double. Écrites dans " +
                        "un dossier surveillé, les copies étaient relues à l'analyse suivante " +
                        "comme s'il s'agissait de nouvelles playlists. Les dossiers d'export " +
                        "sont désormais ignorés à l'import, et les copies déjà entrées sont " +
                        "retirées. Un dossier que vous surveillez vous-même reste lu, lui."
                ),
                amelioration(
                    "Une playlist mise à jour prend le nom « .m3u8 ». C'est le même format, sous " +
                        "le nom qui dit ce qu'il contient : du M3U en UTF-8. Sans cela les " +
                        "accents ressortaient en charabia chez les autres lecteurs."
                )
            )
        ),
        Nouveaute(
            code = 39, nom = "4.7", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Quand le fichier .m3u d'origine ne peut pas être mis à jour, " +
                        "l'application le dit enfin. L'échec était avalé en silence : la " +
                        "playlist changeait dans l'application, le fichier restait tel quel, " +
                        "et rien ne le signalait."
                ),
                correction(
                    "Les chemins écrits dans le .m3u sont maintenant relatifs au dossier de ce " +
                        "fichier, comme l'attendent les autres lecteurs. Une playlist rangée " +
                        "dans un sous-dossier pointait à côté."
                ),
                ajout(
                    "« Réécrire le fichier » dans le menu d'une playlist, et « Réécrire tous " +
                        "les fichiers .m3u » dans Paramètres → Playlists : de quoi remettre les " +
                        "fichiers à jour d'un coup après avoir autorisé l'écriture."
                ),
                amelioration(
                    "Le fichier réécrit l'est toujours au format #EXTM3U complet, avec durées " +
                        "et titres, même si l'original n'était qu'une liste de chemins."
                ),
                ajout(
                    "Quand la réécriture est refusée, l'application propose sur-le-champ " +
                        "d'autoriser l'écriture, et le sélecteur s'ouvre directement sur le " +
                        "dossier concerné."
                ),
                correction(
                    "Choisir un sous-dossier d'un dossier déjà surveillé ne l'ajoute plus : " +
                        "toute sa musique et toutes ses playlists comptaient alors en double. " +
                        "Un dossier déjà en double est signalé dans « Dossiers de musique »."
                )
            )
        ),
        Nouveaute(
            code = 38, nom = "4.6", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Effacer un fichier ne devrait plus être refusé. L'application ne " +
                        "conservait que le droit de lire vos dossiers, jamais celui d'y " +
                        "écrire : Android refusait " +
                        "donc tout effacement, tout déplacement et toute réécriture de " +
                        "playlist. Un dossier ajouté avant cette version reste en lecture " +
                        "seule — Réglages → Paramètres de l'application → Dossiers de " +
                        "musique → « Autoriser l'écriture » et rechoisissez le même dossier."
                ),
                ajout(
                    "« Analyser les dossiers » affiche son bilan à la fin : titres ajoutés ou " +
                        "retirés, playlists importées, mises à jour ou supprimées, ou " +
                        "« Aucune modification »."
                ),
                correction(
                    "Une playlist dont le fichier .m3u a été supprimé du téléphone disparaît " +
                        "maintenant de l'application à l'analyse suivante."
                ),
                ajout(
                    "Le menu d'une playlist (appui central) indique le dossier et le nom du " +
                        "fichier d'où elle vient."
                ),
                ajout(
                    "« Ajouter à une playlist » propose « Nouvelle playlist » : vous choisissez " +
                        "le dossier et le nom du fichier, et le titre y est rangé aussitôt."
                ),
                ajout(
                    "« Exporter les playlists » est maintenant directement dans Réglages, et " +
                        "propose de **partager** les fichiers avec une autre application en " +
                        "plus de les écrire dans un dossier."
                )
            )
        ),
        Nouveaute(
            code = 37, nom = "4.5", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Ouvrir « Paramètres » depuis l'interface moderne ne vous enferme plus dans " +
                        "les réglages : MENU et le bouton retour remontent à « Réglages » puis à " +
                        "l'accueil de la molette. La molette s'ouvrait sur un seul niveau, " +
                        "sans rien au-dessus où revenir."
                ),
                ajout(
                    "Dans ces réglages, une entrée « Revenir à l'interface moderne » vous " +
                        "ramène d'où vous veniez."
                ),
                ajout(
                    "Configurations enregistrées : trois emplacements pour mettre de côté " +
                        "l'apparence de la molette — ambiance et teinte, thème, fond, style, " +
                        "couleur et effet de la molette, liseré, barre de progression — et la " +
                        "rappeler d'un geste. Dans « Réglages » et dans « Réglages de la vue Rétro »."
                ),
                ajout(
                    "Chaque configuration se remplace par l'apparence actuelle ou se supprime, " +
                        "après confirmation. Rappeler une configuration ne touche jamais au son."
                )
            )
        ),
        Nouveaute(
            code = 36, nom = "4.4", date = "septembre 2026",
            lignes = listOf(
                amelioration(
                    "L'écran « Paramètres » de l'interface moderne a disparu : tous les réglages " +
                        "vivaient déjà à la molette, et deux écrans pour les mêmes réglages en " +
                        "faisaient un de trop."
                ),
                amelioration(
                    "« Paramètres », dans le menu de l'interface moderne, ramène à la molette et " +
                        "y ouvre directement les réglages."
                ),
                ajout(
                    "L'export des playlists se choisit maintenant à la molette : le format, " +
                        "puis les playlists à exporter une à une (ou toutes d'un coup), puis le " +
                        "dossier de destination."
                )
            )
        ),
        Nouveaute(
            code = 35, nom = "4.3", date = "septembre 2026",
            lignes = listOf(
                amelioration(
                    "L'application s'ouvre désormais sur la molette. L'interface moderne reste " +
                        "entière — listes et lecteur plein écran — et s'ouvre depuis " +
                        "« Réglages → Interface moderne »."
                ),
                ajout(
                    "« Réglages → Paramètres de l'application » : tous les réglages de " +
                        "l'application se règlent maintenant à la molette. Dossiers de musique, " +
                        "Bibliothèque, Lecture, Qualité sonore, Apparence, Playlists et les " +
                        "formats reconnus."
                ),
                ajout(
                    "Les réglages continus — fondu, vitesse, durée minimale, égaliseur — " +
                        "s'ouvrent sur un cadran que la molette déplace. La valeur essayée ne " +
                        "s'enregistre qu'au bouton central ; MENU rend celle d'avant."
                ),
                ajout(
                    "L'égaliseur complet à la molette : préréglages, chaque bande une par une, " +
                        "correcteur de graves, spatialisation et gain."
                ),
                ajout(
                    "Ajouter ou retirer un dossier de musique, importer un .m3u et exporter " +
                        "les playlists se font sans quitter la molette."
                ),
                amelioration(
                    "La racine du menu ne porte plus qu'une entrée « Réglages » : le réglage de " +
                        "la molette a rejoint les autres à l'intérieur."
                )
            )
        ),
        Nouveaute(
            code = 34, nom = "4.2", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'effet de molette ne se fige plus et ne disparaît plus. La traînée n'avait " +
                        "plus de raison sûre d'être redessinée : elle restait sur sa dernière " +
                        "image, quand elle paraissait."
                ),
                amelioration(
                    "Vue molette, écran de lecture : le fond descend maintenant jusque sous la " +
                        "barre des boutons. La bande vide entre les deux a disparu, et les " +
                        "textes posés dessus restent lisibles grâce à un voile."
                ),
                ajout(
                    "« Palette de couleurs » range désormais vingt-trois ambiances en six " +
                        "catégories : Énergiques / Lumineux, Sombres / Intenses, Doux / " +
                        "Apaisants, Élégants / Sérieux, Rétro / Nostalgiques, Nature / Organique."
                ),
                ajout(
                    "Chaque ambiance — Vif, Néon, Pop, Cyberpunk, Pastel, Luxe, Rétro 80s, " +
                        "Océan… — ouvre une barre de couleurs que la molette parcourt : la " +
                        "teinte se choisit sur tout le cercle, avec un aperçu, et ne " +
                        "s'enregistre qu'au bouton central."
                ),
                amelioration(
                    "Les couleurs que vous aviez choisies sont reprises : l'ancienne palette " +
                        "devient l'ambiance la plus proche, à la même teinte."
                )
            )
        ),
        Nouveaute(
            code = 33, nom = "4.1", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Activer l'aléatoire ne perd plus la file. Le mélange suivait une " +
                        "permutation tirée à l'avance et n'en gardait que ce qui suivait le " +
                        "morceau en cours : de 3 473 titres, il n'en restait parfois que 117."
                ),
                ajout(
                    "Style de barre de progression : classique, ondulé animé, forme d'onde, " +
                        "segments, points, fil fin."
                ),
                ajout(
                    "Option « Affichage liseré sur le lecteur » : le trait de couleur sur le " +
                        "cadre, la molette et les boutons peut être masqué."
                ),
                ajout(
                    "Palettes neutres — blanc, gris, anthracite — pour une interface sans couleur."
                ),
                ajout(
                    "Un bouton dans « Titres les plus écoutés » remet les compteurs à zéro, " +
                        "après confirmation."
                ),
                amelioration(
                    "La molette sur l'écran de verrouillage a été retirée : Android ne permet " +
                        "pas ce qu'elle promettait."
                )
            )
        ),
        Nouveaute(
            code = 32, nom = "4.0", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'ordre alphabétique respecte enfin le français. Les titres commençant par " +
                        "une lettre accentuée — « Éternel », « Ça va trop vite » — étaient " +
                        "rejetés après le Z, parce que la comparaison se faisait sur le code des " +
                        "caractères et non sur l'alphabet."
                ),
                correction(
                    "Le tri et le repère ABC disent maintenant la même chose : le bouton " +
                        "annonçait « E » pour un titre relégué tout en bas de la liste."
                ),
                amelioration(
                    "Artistes, albums, genres et dossiers suivent la même règle."
                )
            )
        ),
        Nouveaute(
            code = 31, nom = "3.9", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'avance et le retour rapides suivent enfin le doigt. La lecture ne se " +
                        "déplace plus à chaque cran mais une seule fois, quand vous arrêtez de " +
                        "tourner — l'écran, lui, répond immédiatement."
                ),
                amelioration(
                    "La durée affichée et la barre montrent tout de suite où vous allez " +
                        "atterrir, sans attendre que le lecteur s'y rende."
                )
            )
        ),
        Nouveaute(
            code = 30, nom = "3.8", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'effet de molette ne reste plus figé à l'écran après le geste, et la roue " +
                        "ne traîne plus derrière le doigt : chaque point de la traînée " +
                        "redessinait toute la molette, soixante fois par seconde."
                ),
                correction(
                    "L'avance rapide à la molette suit le geste. Chaque cran déplaçait la " +
                        "lecture séparément ; les crans rapprochés sont maintenant regroupés."
                ),
                amelioration(
                    "La barre de progression cesse de s'animer pendant qu'on cherche à la " +
                        "molette : elle suit le cran au lieu de le rattraper."
                )
            )
        ),
        Nouveaute(
            code = 29, nom = "3.7", date = "septembre 2026",
            lignes = listOf(
                amelioration(
                    "Les menus de la molette répondent bien plus vite, la file d'attente en " +
                        "premier : chaque cran reconstruisait des milliers de lignes."
                ),
                amelioration(
                    "Un défilement rapide saute désormais au lieu de s'animer ligne à ligne — " +
                        "la molette ne prend plus de retard sur le doigt."
                ),
                correction(
                    "La barre du lecteur ne change plus de taille ni de place selon le mode. " +
                        "Volume, vitesse et position se dessinaient avec trois géométries " +
                        "différentes."
                )
            )
        ),
        Nouveaute(
            code = 28, nom = "3.6", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'effet de molette fonctionne enfin. Le bloc qui lit vos gestes figeait le " +
                        "réglage au premier affichage : il croyait toujours « sans effet »."
                ),
                correction(
                    "Le reflet du style « Verre » débordait de la roue. Tous les reflets sont " +
                        "désormais contenus dans le disque."
                ),
                correction(
                    "Le bouton central garde la couleur de la roue, quel que soit le thème. " +
                        "Il virait au clair ou au noir pendant que la roue gardait sa teinte."
                ),
                correction(
                    "La molette sur l'écran de verrouillage s'ouvre bien depuis la notification."
                ),
                ajout(
                    "Neuf styles de molette de plus : Material, neumorphique, lustré, argile, " +
                        "brutaliste, rétro, dégradé, duotone, métallique."
                )
            )
        ),
        Nouveaute(
            code = 27, nom = "3.5", date = "septembre 2026",
            lignes = listOf(
                ajout(
                    "L'interface molette a désormais son propre thème et sa propre palette, dans " +
                        "Réglages de la vue Rétro. Ceux des réglages principaux ne valent plus que pour " +
                        "l'interface classique."
                ),
                ajout(
                    "Molette sur l'écran de verrouillage, à activer dans Réglages de la vue Rétro. " +
                        "Android interdit de dessiner sur le verrouillage lui-même : la molette " +
                        "s'ouvre donc par-dessus, d'un appui sur la notification, sans déverrouiller."
                ),
                amelioration(
                    "En thème AMOLED, la couleur choisie se voit enfin : la molette et ses " +
                        "réglages de teinte ne dépendent plus du fond noir."
                )
            )
        ),
        Nouveaute(
            code = 26, nom = "3.4", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Vos modifications d'une playlist importée ne sont plus effacées. Chaque " +
                        "analyse relisait le fichier .m3u et remplaçait le contenu : un titre " +
                        "retiré revenait au scan suivant, sans un mot."
                ),
                ajout(
                    "Le fichier .m3u d'origine est maintenant mis à jour quand vous modifiez " +
                        "la playlist dans l'application. Réglable dans Réglages, Bibliothèque."
                ),
                ajout(
                    "Réglages de la vue Rétro : style de molette (creusé, bombé, plat, cerclé, verre), " +
                        "couleur, et effet au toucher (traînée unie, arc-en-ciel, paillettes, onde)."
                ),
                ajout(
                    "Version, numéro de build, auteur et notes de version dans les réglages de " +
                        "la vue molette, sous « À propos »."
                ),
                ajout(
                    "Un bouton en haut de l'interface classique bascule directement vers la " +
                        "molette."
                )
            )
        ),
        Nouveaute(
            code = 25, nom = "3.3", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Le menu d'appui long s'ouvre enfin au bout de deux secondes. Un doigt " +
                        "immobile n'envoie aucun signal : l'application attendait le premier " +
                        "frémissement, d'où les quatre secondes ressenties."
                ),
                correction(
                    "La file d'attente part du morceau en cours. En aléatoire, elle affichait " +
                        "le tirage depuis son début, donc des titres qui ne passeraient jamais."
                ),
                ajout(
                    "La file d'attente garde les morceaux déjà écoutés. L'historique repart à " +
                        "zéro dès qu'une liste est relancée."
                ),
                ajout(
                    "Un bouton en haut à gauche de la molette ramène à la lecture en cours, " +
                        "depuis n'importe quel menu."
                ),
                ajout(
                    "Nouveau menu « Réglages de la vue Rétro », avec le choix du fond du lecteur : " +
                        "par défaut, pochette floutée, ou couleur unie."
                ),
                ajout(
                    "Dans l'interface classique, un appui maintenu sur un titre ou une playlist " +
                        "propose de le supprimer, après confirmation."
                )
            )
        ),
        Nouveaute(
            code = 24, nom = "3.2", date = "septembre 2026",
            lignes = listOf(
                ajout(
                    "Maintenez le centre de la molette deux secondes : un menu s'ouvre pour " +
                        "ranger, déplacer ou effacer ce que vous aviez sélectionné — un titre, " +
                        "un artiste, un album, un dossier, une playlist."
                ),
                ajout(
                    "Nouvelle catégorie « Musique sans playlist » dans le menu Musique : tous " +
                        "les titres que vous n'avez rangés nulle part."
                ),
                ajout(
                    "L'ajout à une playlist se fait maintenant à la molette, dans le style de " +
                        "l'interface — et permet de cocher plusieurs playlists d'un coup."
                ),
                ajout(
                    "Le bouton d'appartenance ouvre la liste des playlists du morceau : on peut " +
                        "l'en retirer, une par une ou toutes, après confirmation. Le fichier, lui, " +
                        "n'est pas touché."
                ),
                ajout(
                    "Export des playlists depuis les réglages, au format M3U8, M3U ou XSPF, " +
                        "toutes ou seulement celles que vous choisissez."
                ),
                amelioration(
                    "Les réglages affichent la version exacte de l'application et son auteur."
                )
            )
        ),
        Nouveaute(
            code = 23, nom = "3.1", date = "septembre 2026",
            lignes = listOf(
                ajout(
                    "Cet écran : à chaque mise à jour, l'application annonce désormais ce qui " +
                        "a changé. On le retrouve à tout moment dans Réglages, À propos."
                )
            )
        ),
        Nouveaute(
            code = 22, nom = "3.0", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Un titre déjà présent dans une playlist n'y est plus ajouté une seconde " +
                        "fois — et l'application vous le dit, au lieu de ne rien répondre."
                ),
                correction(
                    "La file d'attente montre enfin le vrai ordre de lecture. En aléatoire, " +
                        "elle affichait la liste alphabétique pendant que le son suivait le tirage."
                ),
                ajout(
                    "Une marque signale les titres qui n'appartiennent à aucune playlist, dans " +
                        "les listes comme sur l'écran de lecture."
                ),
                correction(
                    "Les messages de l'application s'affichent aussi dans l'interface molette, " +
                        "où ils se perdaient depuis le début."
                )
            )
        ),
        Nouveaute(
            code = 21, nom = "2.9", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "L'application ne se ferme plus sur les onglets Dossiers, Artistes, Albums " +
                        "et Playlists quand deux dossiers de musique portent le même nom."
                ),
                ajout(
                    "Bouton ABC dans l'interface molette : chaque cran saute à la première " +
                        "chanson de la lettre suivante."
                )
            )
        ),
        Nouveaute(
            code = 20, nom = "2.8", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "Un fichier .m4a sans perte n'est plus étiqueté « AAC » : son vrai codec est " +
                        "lu dans le fichier lui-même."
                ),
                amelioration(
                    "L'interface molette ne retrie plus la bibliothèque à chaque seconde " +
                        "pendant la lecture."
                )
            )
        ),
        Nouveaute(
            code = 18, nom = "2.7", date = "septembre 2026",
            lignes = listOf(
                correction(
                    "« Toutes les chansons » se lit de nouveau. Une liste de lecture .m3u " +
                        "rangée par erreur parmi les morceaux faisait refuser la file entière."
                ),
                amelioration(
                    "Les fichiers .m3u trouvés dans vos dossiers deviennent de vraies playlists."
                )
            )
        ),
        Nouveaute(
            code = 17, nom = "2.6", date = "septembre 2026",
            lignes = listOf(
                ajout(
                    "La couleur des titres dit leur qualité : turquoise en haute résolution, " +
                        "or sans perte, argent pour les compressions récentes."
                ),
                amelioration("Plus aucune limite de taille sur la file de lecture.")
            )
        )
    )

    /** La version en tête du journal, celle que l'application annonce si tout le reste échoue. */
    val derniere: Nouveaute? get() = journal.firstOrNull()
}

/**
 * Faut-il annoncer les nouveautés au démarrage ?
 *
 * ⚠️ Trois refus, et le premier est celui qu'on oublie : **une installation neuve n'a rien de
 * nouveau à annoncer**. Dérouler un journal des versions à quelqu'un qui ouvre l'application
 * pour la première fois ne lui apprend rien et lui barre l'écran.
 *
 * [premiereInstallation] se lit sur le système : une application installée et jamais mise à jour
 * a la même date de pose et de dernière mise à jour.
 */
fun doitAnnoncerLesNouveautes(
    derniereVue: Int,
    versionActuelle: Int,
    premiereInstallation: Boolean
): Boolean = when {
    premiereInstallation && derniereVue == 0 -> false
    versionActuelle <= derniereVue -> false
    else -> true
}

/**
 * Les versions à montrer : celles arrivées depuis la dernière fois.
 *
 * Quand on saute plusieurs versions d'un coup, on les voit toutes, de la plus récente à la plus
 * ancienne. Quand rien n'est mémorisé — une mise à jour venue d'avant cette mécanique — on s'en
 * tient à la version installée, plutôt que de dérouler tout l'historique.
 */
fun nouveautesDepuis(
    journal: List<Nouveaute>,
    derniereVue: Int,
    versionActuelle: Int
): List<Nouveaute> {
    val retenues = if (derniereVue <= 0) {
        journal.filter { it.code == versionActuelle }
    } else {
        journal.filter { it.code in (derniereVue + 1)..versionActuelle }
    }
    return retenues
        .ifEmpty { journal.filter { it.code <= versionActuelle }.take(1) }
        .sortedByDescending { it.code }
}

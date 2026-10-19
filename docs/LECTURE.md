# La lecture

À lire avant de toucher à `lecture/`.

## Qui détient quoi

```
Interface (Compose)  ──►  Lecteur (MediaController)  ──►  ServiceLecture (MediaSession + ExoPlayer)
                                                              ▲            ▲            ▲
                                                    notification    casque / BT    écran verrouillé
```

L'interface **ne parle jamais à ExoPlayer**. Elle commande un `MediaController`, exactement comme
la notification ou les boutons du casque : c'est ce qui rend impossible que l'écran affiche
« lecture » pendant que la notification affiche « pause ».

Le service et l'interface vivent dans le même processus, ce qui autorise deux raccourcis
assumés : `SortieAudio` (objet global) publie le format décodé sans passer par des `Bundle` de
session, et le service enregistre la file et les statistiques directement dans le `Depot`.

## La chaîne audio

```kotlin
DefaultRenderersFactory(this)
    .setEnableAudioFloatOutput(reglages.sortieFlottante) // pas de troncature à 16 bits
    .setEnableAudioTrackPlaybackParams(true)             // vitesse gérée par le matériel
    .setEnableDecoderFallback(true)                      // décodeur logiciel en secours
```

- **Aucun effet n'est instancié tant que l'égaliseur est éteint.** C'est la garantie qu'un FLAC
  24 bits sort intact par défaut ; `Egaliseur.detacher()` libère réellement les effets.
- Le **déchargement audio** (DSP) se règle par `TrackSelectionParameters.AudioOffloadPreferences`
  — et non par l'ancien `DefaultRenderersFactory.setEnableAudioOffload`, retiré depuis media3 1.3.
  Il est **incompatible avec les effets** : le code force `AUDIO_OFFLOAD_MODE_DISABLED` dès que
  l'égaliseur est allumé, sinon l'utilisateur bougerait des curseurs sans rien entendre changer.
- Changer la **sortie flottante** exige de reconstruire les rendus : le réglage annonce donc
  honnêtement « prend effet au prochain démarrage de l'application ».

## Toute la bibliothèque dans la file

Une `MediaSession` republie sa file entière vers la session du **système** à chaque changement,
et `MediaSessionLegacyStub.setQueue()` ne la tronque jamais — alors que media3 se donne par
ailleurs 256 Ko de marge. Un `MediaItem` de ce projet pèse ~1 550 octets (`GrandeFileTest`) :
au-delà d'environ 168 titres, la transaction dépasse ce que le noyau accepte et la commande est
perdue **en silence**, le `MediaController` l'ayant déjà appliquée chez lui. D'où, pendant
plusieurs versions, un écran parfaitement juste devant un lecteur resté sur l'ancienne file.

La parade n'est pas de rogner la file, c'est de **ne pas la publier**. `isQueueEnabled()` ne
consulte qu'une chose : `COMMAND_GET_TIMELINE` dans les commandes du **contrôleur de
notification**. `ServiceLecture.rappels.onConnect` la lui retire — à lui seul :

```kotlin
if (session.isMediaNotificationController(controleur)) {
    commandes.buildUpon().remove(Player.COMMAND_GET_TIMELINE).build()
}
```

L'écran verrouillé, la notification et les boutons du casque lisent le morceau en cours, pas la
liste : ils ne perdent rien. Notre propre contrôleur garde la file complète, et « Toutes les
chansons » peut porter les cinq mille titres de la bibliothèque.

### Ce que cette taille impose ailleurs

- `Lecteur.rafraichir` ne reconstruit la file affichée **que si la chronologie a changé** —
  sinon cinq mille titres étaient reparcourus deux fois par seconde pour afficher une position.
- La carte URI → piste est gardée tant que la bibliothèque ne change pas.
- `ServiceLecture` ne rejoint la liste des URI que sur changement de file, et n'enregistre la
  session que toutes les 30 s : les réglages vivent dans un `DataStore`, réécrit en entier à
  chaque modification.

## Changer de file en pleine lecture

`Lecteur.jouer` coupe et vide avant de reposer la nouvelle liste :

```kotlin
c.stop()
c.clearMediaItems()
c.setMediaItems(pistes.map(::element), depart, repriseDe(pistes[depart]))
c.prepare()
c.play()
```

Le `stop()` et le `clearMediaItems()` ne sont pas décoratifs. Remplacer une file pendant que le
son coule laisse ExoPlayer réutiliser sa chaîne de décodage ; l'écran affichait alors le morceau
demandé pendant que le haut-parleur terminait le précédent, puis enchaînait sur l'ancienne liste.
Repartir d'un lecteur à l'arrêt coûte une fraction de seconde et supprime cette zone grise.

Le `MediaController` applique toute commande **chez lui d'abord**, avant que la session ne
l'accepte : ce que l'écran montre n'est donc jamais une preuve de ce que le lecteur fait. C'est
pourquoi `SortieAudio.source` publie, depuis les événements de la chaîne audio, le titre que le
décodeur traite réellement — visible dans « Informations sur le fichier », et affiché seulement
quand il diffère de ce que l'interface annonce.

## Un fichier illisible ne condamne pas la file

Une `PlaybackException` laisse ExoPlayer en `STATE_IDLE`. La file reste affichée, intacte, mais
**plus rien ne démarre** tant que personne ne rappelle `prepare()` : un seul fichier abîmé rendait
donc muet tout ce qui le suivait, et l'utilisateur n'avait aucun moyen de comprendre pourquoi.

`Lecteur.onPlayerError` saute désormais au morceau suivant et réamorce. La décision est isolée
dans `actionApresEchec(echecsDeSuite, aUnSuivant)`, donc testable : on saute, on repose le lecteur
sur le dernier morceau, et on abandonne au-delà de trois échecs enchaînés — devant une file
entièrement illisible, dévaler la bibliothèque serait pire que s'arrêter sur le message d'erreur.
Le compteur repart à zéro dès qu'un morceau démarre vraiment.

Le message d'erreur porte en clair le code media3 (`errorCodeName`) : sans lui, « lecture
impossible » ne dit pas si le fichier est absent, refusé, ou d'un format qu'aucun décodeur ne
prend.

## Ce que la session accepte

La session déclare un `MediaSession.Callback`. Sans lui, media3 refuse toute file venue d'un
contrôleur qui n'envoie que des identifiants — Android Auto, l'assistant vocal, une montre —
puisque le rappel par défaut exige une URI. Ici l'identifiant **est** l'URI du document SAF, donc
`resoudre()` la remet simplement en place. Les éléments sans URI ni identifiant sont écartés :
sinon la session rejetterait la file entière pour un seul élément vide.

## La file affichée est l'ordre de lecture

⚠️ Activer l'aléatoire **ne réordonne pas** la file d'ExoPlayer : il change la façon dont elle
est parcourue. Lire la liste de 0 à n-1 donnait donc l'ordre alphabétique alors que le son
suivait le tirage — c'est exactement ce que montrait l'écran « file d'attente ».

`ordreDeLecture(chronologie, melange)` remonte le vrai parcours, de `getFirstWindowIndex` en
`getNextWindowIndex`. `Lecteur` garde à côté la table `positionsVues`, qui ramène chaque ligne
affichée à la position que le lecteur, lui, connaît : sans elle, toucher la troisième ligne d'une
file mélangée lancerait le troisième titre de l'alphabet.

## Aléatoire et répétition

`shuffleModeEnabled` d'ExoPlayer tire un ordre complet et le suit jusqu'au bout : **aucun titre ne
revient tant que la file n'est pas épuisée**, ce qui est exactement ce que promet le bouton
« Aléatoire ». Ne pas remplacer par un tirage à chaque piste.

La répétition porte sur la file en cours, quelle qu'en soit l'origine (tous les titres, une
playlist, un dossier) : `REPEAT_MODE_ALL` boucle la sélection, `REPEAT_MODE_ONE` le titre.

## Le fondu

Un vrai fondu enchaîné demanderait **deux lecteurs qui se recouvrent** — donc deux décodages
simultanés. Ici l'atténuation est franche : le morceau se lève et s'éteint sur `fonduMs`.

- Aux **frontières de piste**, c'est le battement de 100 ms du service qui ajuste `player.volume`.
- À la **pause et à la reprise**, c'est un `ForwardingPlayer` (`LecteurAvecFondu`) qui enveloppe
  ExoPlayer : toutes les commandes passent par lui, d'où qu'elles viennent (notification, casque,
  écran). Il appelle le lecteur interne par sa référence directe — Kotlin interdit `super` depuis
  une lambda, et c'est bien à la fin de l'atténuation qu'il faut couper.
- Le drapeau `fonduManuel` empêche le battement et la rampe de se disputer le volume.

## Le battement (100 ms)

Un seul `Runnable` fait trois choses tant que la lecture tourne :

1. le fondu de début et de fin de piste ;
2. le **comptage d'écoute** au-delà de quinze secondes (une fois par piste, pas à chaque tour) ;
3. toutes les cinq secondes, l'enregistrement de la file (pour la reprise au démarrage) et, pour
   les pistes de plus de dix minutes, la **position de reprise**.

Reprendre un morceau de trois minutes là où on l'avait laissé serait déroutant ; sur une heure
d'enregistrement, repartir de zéro l'est bien davantage — d'où le seuil.

## Reprise de session

Le service écrit `fileMemorisee` / `indexMemorise` / `positionMemorisee` dans les réglages ; au
démarrage suivant, `Lecteur.reprendreLaSession()` attend que la bibliothèque soit chargée
(`depot.pistes.first { it.isNotEmpty() }`) avant de rattacher les URI à des pistes, puis
**prépare sans jouer**.

⚠️ **La condition se vérifie deux fois : avant *et après* cette attente.** Charger la bibliothèque
prend un instant, et l'utilisateur est souvent plus rapide : s'il lance un morceau pendant ce
temps, la reprise doit renoncer. Sans cette seconde vérification, la file d'hier revenait écraser
celle qu'il venait de choisir, quelques secondes après — on lançait un dossier, et la musique
repartait sur le genre écouté la veille. `doitReprendre` (fonction pure, testée) porte la règle :
reprise demandée, souvenir non vide, **aucun choix de l'utilisateur**, file vide.

Une demande de lecture arrivée **avant** la connexion au service (ouverture d'un fichier depuis un
explorateur : l'activité et le contrôleur démarrent en même temps) est mise de côté dans
`enAttente` et rejouée à la connexion — sans quoi elle serait perdue une fois sur deux.

## Fermeture

`onTaskRemoved` ne coupe la lecture **que si elle est déjà à l'arrêt**. Balayer l'application des
tâches récentes pendant qu'elle joue ne doit pas arrêter la musique : personne n'attend cela d'un
lecteur.

## Ce que Robolectric sait faire — et ne sait pas faire

La liaison factice d'un `MediaSessionService` renvoie un `ComponentName` nul, sur lequel media3
s'effondre avant même de prévenir l'appelant. Les tests d'interface déclarent donc la liaison
impossible (`declareActionUnbindable`) : le lecteur reste « non connecté », ce qui est exactement
l'état de l'écran tant qu'aucune musique n'a démarré.

`SessionAllerRetourTest` contourne le service : il monte une `MediaSession` sur un vrai ExoPlayer
— câblé comme le service, `FabriqueRendus36` et enveloppe de fondu comprises — et lui branche un
`MediaController`. Il vérifie alors ce que l'écran ne peut pas prouver : que la file arrive
jusqu'au **lecteur**, URI comprise, y compris quand on remplace une liste en cours de lecture.

Sa limite tient en une phrase : aucun rendu ne tourne. Robolectric n'a ni décodeur ni piste
audio, donc ces tests couvrent le transport des commandes, jamais le son qui en sort.

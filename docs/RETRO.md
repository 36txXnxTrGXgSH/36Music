# La vue Rétro (l'interface à molette)

À lire avant de toucher à `ui/retro/`.

**C'est l'interface principale depuis la 4.3** : l'application s'ouvre dessus. Un écran unique —
un « écran » rectangulaire en haut, une molette cliquable en bas. Pour voir l'interface moderne :
molette → **Réglages → Interface moderne** ; pour revenir : son menu ⋮ → **Paramètres**, qui
ramène ici et ouvre directement `NiveauRetro.ParametresApp` (voir `DepartRetro`).

⚠️ **L'interface moderne n'a plus d'écran de réglages depuis la 4.4** (`EcranParametres.kt`,
`EcranPalettes.kt` et le dialogue d'export ont été supprimés). Deux écrans réglant les mêmes
champs d'`EtatReglages`, c'était un de trop : celui qui reste est celui qu'on atteint sans lâcher
la roue.

⚠️ Conséquence directe : **tout réglage doit être atteignable à la molette.** Un réglage qui ne
vivrait que dans l'écran « Paramètres » de l'interface moderne serait un réglage perdu pour qui
ne quitte jamais la roue. C'est la raison d'être de `NiveauRetro.ParametresApp` et de ses
sous-niveaux, qui reprennent une à une les sections de cet écran et modifient **les mêmes champs**
d'`EtatReglages` — il n'y a pas deux jeux de réglages.

## Les cinq gestes, et rien d'autre

| Geste | Dans un menu | Sur l'écran de lecture |
| --- | --- | --- |
| Tourner sur l'anneau | déplace la sélection | **volume**, ou avance/recul, ou vitesse |
| Appui central | ouvre l'entrée sélectionnée | lecture / pause |
| MENU (haut) | remonte d'un niveau | revient au menu |
| ⏮ / ⏭ (gauche / droite) | piste précédente / suivante | idem |
| ▶⏸ (bas) | lecture / pause | idem |

**Les lignes de la liste ne sont pas cliquables.** C'est délibéré : une liste tactile doublée
d'une molette ne serait qu'une application ordinaire déguisée, et la molette n'y servirait à rien.
Les seules exceptions sont les six boutons de l'écran de lecture (ci-dessous) : ils commandent des
réglages, pas la navigation.

Chaque ligne porte sa valeur **au bout**, alignée à droite — un décompte dans les menus, la durée
pour un titre. C'est là qu'on la cherche sur un baladeur à molette, et la liste se lit d'un coup d'œil.

Le bouton retour d'Android est câblé sur MENU, et n'est actif que s'il reste un niveau à remonter.

## L'écran de lecture et ses six commandes

L'écran donne, autour de la pochette : **titre** et **artiste** au-dessus ; **album · année**,
**qualité décodée** (avec le badge Hi-Res s'il y a lieu) et la **sortie matérielle** en dessous.

Trois commandes tactiles dans la barre de titre : **favori**, **ajout à une playlist**, et un
bouton **information** qui ouvre le menu du morceau — *Lire ensuite*, *Ajouter à la file
d'attente*, *Aller à l'album*, *Aller à l'artiste*, *Informations sur le fichier*. Ce menu s'ouvre
au doigt mais **se parcourt à la molette**, comme tout le reste : un menu tactile au milieu d'une
interface à molette serait un corps étranger. La fiche du fichier reprend les métadonnées lues,
rangées en lignes de menu — libellés courts avec la valeur au bout, valeurs longues (titre,
chemins) en dessous.

⚠️ **La pochette a une taille calculée, pas une taille relative.** La première version posait la
pochette et le texte côte à côte, la pochette en `fillMaxHeight().aspectRatio(1f)` : sur un écran
haut, elle réclamait une largeur égale à la hauteur disponible, dévorait toute la rangée, et la
colonne de texte se retrouvait à **zéro pixel** — on ne voyait plus une seule information. La
disposition est donc en colonne, avec un côté déduit des contraintes réelles :
`min(largeur ; hauteur − RESERVE_TEXTES)`. Une simple fraction de la hauteur, essayée ensuite,
laissait deux grandes bandes vides — la pochette doit prendre **toute** la place que les textes ne
réclament pas. Même règle dans le carrousel.

Dans la barre de titre : **favori** (cœur) et **ajout à une playlist** — les deux gestes qu'on ne
veut pas aller chercher dans un menu pendant qu'un morceau passe.

Aux quatre coins que la molette laisse libres :

| Coin | Bouton | Effet |
| --- | --- | --- |
| haut-gauche | avance / recul | bascule : la rotation déplace la lecture de **5 s par cran** |
| haut-droite | vitesse | bascule : la rotation change la vitesse de **0,05× par cran** (0,5 à 2,5) |
| bas-gauche | aléatoire | active ou coupe la lecture aléatoire |
| bas-droite | répétition | aucune → toute la sélection → le titre seul |

Les deux premiers sont des **bascules exclusives** qui changent ce que commande la rotation
(`ModeMolette`) : réappuyer, ou activer l'autre, rend la molette au volume. Le mode en cours se
lit sous la pochette — jauge de volume, jauge de vitesse, ou barre de progression épaissie — et
dans la ligne d'aide sous la molette. Sans ce retour visible, on ne saurait pas ce que la
prochaine rotation va faire.

## La molette (`Molette.kt`)

**Un seul détecteur, écrit à la main** (`awaitEachGesture`), qui suit le doigt du poser au relever :

- il mesure l'angle (`atan2` depuis le centre), normalise l'écart dans `[-π, π]` — sans quoi le
  passage par 180° ferait bondir d'un tour complet — et émet un cran tous les **15°** (24 crans
  par tour) ;
- un geste parti du disque central ou de l'extérieur de l'anneau ne fait rien tourner ;
- au relever, si aucun cran n'a été émis **et** que le doigt n'a pas glissé au-delà du seuil
  système, c'est un appui : `zoneDe` dit lequel des quatre quartiers (découpés à 45°) ou le centre.

⚠️ **Ne pas revenir à `detectTapGestures` + `detectDragGestures` empilés** : c'était la version
1.1, où les deux détecteurs se disputaient le pointeur et où la rotation ne partait jamais. Le
détecteur unique tranche lui-même entre tourner et appuyer, et démarre dès le premier cran, sans
attendre le seuil de glissement de Compose — c'est le toucher de l'appareil d'origine.

⚠️ **Le retour haptique n'est pas cosmétique.** Chaque cran déclenche un `CLOCK_TICK`, chaque appui
un `VIRTUAL_KEY`. Sans ce tic, on ne sait pas si le geste a été compris et la molette devient
injouable — c'était déjà le secret de l'appareil d'origine.

### ⚠️ Le piège des commandes figées

`pointerInput(Unit)` **capture ses lambdas à la première composition** et ne les remplace que si
la clé change. En 1.1, `onCran` et `onZone` commandaient donc éternellement le menu du premier
affichage : la liste défilait bien sous le doigt, mais le bouton central rouvrait toujours la
première entrée, et la sélection ne descendait jamais au-delà de la deuxième ligne.

Deux défenses, gardées ensemble :

1. `Molette` passe par `rememberUpdatedState` pour appeler la **version courante** des rappels ;
2. `tourner` et `appuyer`, dans `EcranRetro`, ne lisent **que des objets d'état partagés** — la
   pile `pile`, la carte `selections`, et `entreesCourantes` (un `MutableState` rafraîchi par
   `SideEffect` à chaque composition). Elles ne touchent jamais aux variables locales de la
   composition, qui seraient périmées.

C'est la seconde qui rend le comportement indépendant des subtilités de recomposition : une
commande qui ne lit que de l'état partagé ne peut pas dater.

### Ce que les tests couvrent

- `MoletteTest` : la géométrie des quartiers, sans Compose.
- `MoletteGestesTest` : le composant seul — un quart de tour donne bien six crans, le sens est
  respecté, une rotation ne vaut jamais un appui, un geste parti du centre ne tourne pas.
- `EcranRetroTest` : de bout en bout — tourner puis valider ouvre la **bonne** entrée, la molette
  commande le menu ouvert et non le premier, MENU remonte. Les deux premiers reproduisent
  exactement les blocages de la 1.1.
- `EcranLectureRetroTest` : les six commandes tactiles de l'écran de lecture, et le fait que les
  modes de molette soient des bascules exclusives.
- `CoverFlowTest` : le seul test d'interface qui **remplit la base** — un carrousel ne se juge pas
  sur une bibliothèque vide. Il descend le menu à la molette jusqu'à Cover Flow, vérifie l'album
  affiché, puis ouvre la superposition et y retrouve les titres.

## Cover Flow

*Musique → Cover Flow* : les pochettes en carrousel, une par album, que la molette fait défiler.
Le bouton central ouvre un **sous-menu superposé** — les titres de l'album, par-dessus le
carrousel resté visible en arrière-plan, atténué. MENU referme la superposition sans quitter le
carrousel.

Le carrousel n'est pas une liste défilante : les pochettes sont posées à plat dans un `Box` et
inclinées par `graphicsLayer` (`rotationY`, `scaleX/Y`, `translationX`, `cameraDistance`), leur
ordre de dessin réglé par `zIndex(-|écart|)` — sans quoi une pochette de bord passerait devant
celle du centre. Une `LazyRow` aurait rendu tout cela inutilement compliqué, pour un défilement
qui n'a pas lieu au doigt mais **d'un cran de molette**.

Deux conséquences heureuses :

- la position est un `Float` animé (`animateFloatAsState`), donc le passage d'un album à l'autre
  glisse au lieu de sauter ;
- seules neuf pochettes sont dessinées autour de la sélection : mille albums ne coûtent pas plus
  cher que dix.

⚠️ **Cover Flow n'a pas de mécanique propre.** Ses « entrées » sont les albums, exactement comme
un menu ; la molette y déplace la même sélection, et seul l'affichage change. C'est ce qui fait
tenir la fonctionnalité en une centaine de lignes, et ce qu'il ne faut pas défaire.

## Les titres les plus écoutés

*Musique → Titres les plus écoutés* : les cent titres les plus joués, numérotés, avec leur nombre
d'écoutes au bout de la ligne.

Les compteurs ne datent pas de cette fonctionnalité : le service de lecture valide une écoute
**au-delà de quinze secondes** (`SEUIL_ECOUTE`) et l'inscrit dans la table `statistique`, dont la
clé est l'URI du fichier. Ils survivent donc aux réanalyses comme aux redémarrages, et alimentaient
déjà « Écoutés récemment » dans la recherche.

Le classement lui-même est une fonction pure, `List<Piste>.plusEcoutes(statistiques, limite)` :
tri par nombre d'écoutes, puis par écoute la plus récente à égalité, puis par titre. **Un titre
jamais joué n'y entre pas** — une liste complétée par des zéros ne serait plus un classement mais
la bibliothèque dans le désordre. Et comme l'ordre n'est pas alphabétique, le repère à lettres n'y
apparaît pas.

## Le repère alphabétique

Pendant un défilement rapide, une grande lettre s'affiche au centre : sur trois cents titres, les
libellés passent trop vite pour être lus, l'initiale est la seule information utile.

⚠️ **Le repère n'a de sens que sur une liste rangée par nom.** Toutes les listes de la vue molette
sont donc triées alphabétiquement — y compris les titres d'un artiste, d'un genre ou d'un dossier,
que le reste de l'application range autrement. Les deux exceptions assumées, **un album** (ordre du
disque) et **une playlist** (ordre voulu par l'utilisateur), n'affichent pas de lettre :
`estAlphabetique` en décide, et un test le vérifie.

⚠️ **La liste triée sert aussi à la lecture** : `jouer(liste, index)` reçoit exactement la liste
affichée. Trier l'affichage sans trier la liste jouée lancerait le mauvais morceau.

Deux conditions, décidées par `doitAfficherLaLettre` (fonction pure, donc testée) :

- la liste compte au moins **15 entrées** — en deçà elle tient à l'écran ;
- moins de **260 ms** séparent deux crans — sur un cran isolé, l'utilisateur lit le libellé
  qu'il vient d'atteindre, et un carré posé au milieu de l'écran le masquerait.

La lettre s'efface 900 ms après le dernier cran. `initialeDe` la calcule sans accent ni casse
(« Éléphant » et « elephant » partagent leur repère) et range tout ce qui ne commence pas par une
lettre sous « # », comme un index de bibliothèque. Le carrousel en est exclu : on y reconnaît les
albums à leur pochette, pas à leur initiale.

## Les menus (`EcranRetro.kt`)

La navigation est une **pile de `NiveauRetro`** ; chaque niveau est une simple description
(`Artiste("Netsky")`, `Dossier("Musique/Rock")`…) et ses entrées sont construites par
`entreesPour(niveau, …)`, une fonction **sans état**.

⚠️ **Cette fonction est appelée à deux moments : à l'affichage, et au moment précis où le doigt
agit** (depuis `tourner` et `appuyer`). C'est ce qui garantit que la commande porte sur le menu
réellement ouvert. La version précédente gardait les entrées dans un `MutableState` rafraîchi par
`SideEffect` : le doigt pouvait alors valider une entrée d'un menu déjà quitté, et l'on lançait la
liste d'un genre en croyant lancer celle qu'on avait sous les yeux — la file restait celle du
genre, morceau après morceau. Rien n'est capturé : le niveau vient de la pile, les données des
`StateFlow` du dépôt, tous lus à l'instant de l'appel.

Le menu racine propose **File d'attente** dès qu'une lecture est en cours : ce qui va être joué,
dans l'ordre, avec le morceau courant marqué. C'est la réponse directe à « pourquoi ça enchaîne
sur autre chose ? ».

La sélection de chaque niveau est mémorisée dans une `Map` indexée par la description du niveau :
en remontant, on retrouve la ligne qu'on avait quittée, comme sur un baladeur à molette.

Arborescence : `Musique` (Chansons, Artistes, Albums, **Cover Flow**, Genres, Playlists, Dossiers,
Favoris, **Titres les plus écoutés**),
`Lecture en cours`, `Lecture aléatoire`, `Réglages`. Les entrées portent leur décompte à droite,
les titres leur durée.

Le volume passe par `AudioManager.adjustStreamVolume(STREAM_MUSIC, …, 0)` — le dernier argument
est zéro pour **ne pas** afficher la jauge du système : l'écran de lecture affiche la sienne, qui
s'efface au bout de 1,2 s.

Le menu **Réglages** de la molette porte l'état de la bibliothèque (nombre de titres, avancement
de l'analyse) : cette interface n'a pas de bandeau de messages, un « Analyse lancée » y serait
perdu.

## La couleur des lignes

Chaque ligne de titre porte la couleur du **rang de qualité** de son fichier : turquoise en haute
résolution, or sans perte, argent pour les compressions récentes (AAC/M4A, Opus, Vorbis), couleur
de texte habituelle pour le reste — MP3 en tête. Quatre rangs, pas davantage : la couleur doit se
lire en défilant, et une nuance qu'on doit interpréter ne vaut pas mieux qu'aucune couleur.

Le rang est une propriété du modèle (`Piste.rangQualite`), donc identique partout, et son nom
figure dans « Informations sur le fichier » — la légende se laisse ainsi apprendre sans qu'on ait
à l'écrire nulle part.

Les couleurs suivent la règle du thème : on ne fixe pas un « or » une fois pour toutes, on vise
une **luminance**, sinon l'or lisible sur fond sombre devient illisible sur fond clair.
`QualiteCouleurTest` mesure les trois teintes contre le fond réel des lignes dans les dix-huit
palettes et les trois modes, au seuil WCAG de 4,5:1.

Un liseré de 3 dp à l'entame de la ligne reprend la même couleur : il garde la qualité visible
sur la ligne **sélectionnée**, dont le texte doit rester lisible sur l'accent.

## Comment le code est réparti

Le tout tenait dans un fichier de 1 300 lignes qui mélangeait quatre sujets. Il en fait cinq :

| Fichier | Ce qu'il porte |
|---|---|
| `NiveauRetro.kt` | la pile de menus comme **donnée**, le titre de chaque niveau, le repère alphabétique |
| `EntreesRetro.kt` | le contenu de chaque niveau (`entreesPour`) et le souvenir des listes triées |
| `ListeRetro.kt` | le rendu : barre de titre, liste défilante, menu d'accueil |
| `LectureRetro.kt` | l'écran de lecture et les quatre boutons autour de la roue |
| `EcranRetro.kt` | l'assemblage et les gestes |

### Pourquoi un souvenir des listes

`EcranRetro` lit l'état de lecture, donc **recompose deux fois par seconde** pendant qu'un morceau
joue. Sans `MemoRetro`, « Toutes les chansons » retriait la bibliothèque entière à chacune de ces
recompositions — un tri complet de plusieurs milliers de titres pour faire avancer une barre de
progression.

Les listes s'y comparent par **identité** : comparer leur contenu coûterait exactement ce que le
souvenir cherche à éviter, et un `StateFlow` ne republie de toute façon une liste que lorsqu'elle
a changé. Les clés simples — identifiant d'album, de genre — se comparent par valeur.

Le souvenir est créé par `remember` et **passé** à `entreesPour`, jamais gardé dans un état
global : `entreesPour` reste une fonction sans état, appelée aussi bien à la composition que
depuis les gestes de la molette. C'est ce qui avait été cassé en 2.2, et la leçon tient.

## Les réglages à la molette (4.3)

`Réglages` porte trois entrées de configuration, et une seule racine y mène :

| Entrée | Ce qu'elle règle |
| --- | --- |
| Paramètres de l'application | dossiers, bibliothèque, lecture, son, apparence, playlists |
| Réglages de la vue Rétro | ce qui n'a de sens que la molette en main (fond, molette, palette…) |
| Interface moderne | bascule `StyleInterface` |

### Les oui/non

Pas de case à cocher : l'état se lit **au bout de la ligne** (« oui » / « non »), et l'appui
central le retourne. Une case à cocher supposerait un doigt, et ce n'est pas ainsi que cette
interface se conduit.

### Les cadrans

Un réglage continu ouvre `EcranNombreRetro` : une valeur, une jauge, les deux bornes, et la
molette qui déplace la valeur d'un `pas` par cran.

⚠️ Deux règles, héritées de la barre de couleurs :

- **rien ne s'enregistre avant l'appui central.** MENU rend la valeur d'avant, ce qui permet
  d'essayer une vitesse de lecture sans avoir à retrouver la sienne ;
- **le cadran ne boucle pas**, contrairement au cercle chromatique : passer de 2,5× à 0,5× en un
  cran serait une mauvaise surprise, jamais une intention.

Le `pas` de `ReglageNumerique` n'est pas une commodité d'affichage : c'est la granularité du
geste, et il décide du nombre de crans nécessaires pour traverser la plage. `ParametresRetroTest`
vérifie qu'aucune plage ne demande plus de deux cents crans.

⚠️ Les bandes de l'égaliseur n'ont **pas** de bornes écrites d'avance : nombre de bandes et plage
de gain viennent du matériel. Leur cadran est donc construit dans `EcranRetro`, à partir de
`InfoEgaliseur`, et non déclaré dans le modèle.

## Ouvrir la molette ailleurs qu'à la racine (4.5)

`EcranRetro(depart, depuisModerne)` ne pose **jamais** le seul niveau demandé : `pileDeDepart`
remet les parents en place (`[Racine, Reglages, ParametresApp]`). En 4.4, la pile ne contenait
que `ParametresApp` : MENU et le bouton retour ne remontent que s'il reste un niveau au-dessus,
et l'utilisateur restait enfermé dans les réglages.

`depuisModerne` ajoute *Revenir à l'interface moderne* en tête de `ParametresApp`. Le drapeau
voyage avec la demande dans `DepartRetro` et se consomme avec elle.

## Configurations enregistrées (4.5)

`ConfigurationApparence` regroupe les onze réglages d'apparence de la molette — et **rien
d'autre** : rappeler des couleurs ne doit jamais changer le son. Trois emplacements, stockés
chacun en une chaîne (`configuration_retro_1..3`), format `a1|…|date`.

⚠️ Deux pièges verrouillés par `ConfigurationsRetroTest` :

- la teinte s'écrit avec `Float.toString()`, jamais `format` — un téléphone en français
  écrirait « 212,5 », illisible à la relecture ;
- un emplacement abîmé se relit **vide**, sans exception : une sauvegarde corrompue ne doit pas
  empêcher d'ouvrir le menu qui permet de la remplacer.

⚠️ Piège de test : les lignes de menu lisent `depot.etatReglages`, pas le `LocalReglages` fourni
au test. Un test qui veut voir une configuration doit l'écrire dans le vrai magasin
(`depot.reglages.modifier` en `runBlocking`), puis attendre la ligne.

## Playlists et analyse (4.6)

⚠️ **Le droit d'écrire se prend au moment où le dossier est choisi, et jamais après.**
`ajouterRacine` ne persistait que `FLAG_GRANT_READ_URI_PERMISSION` : Android refusait donc tout
effacement (« le fichier est toujours là »), tout déplacement et toute réécriture de `.m3u`,
sans qu'aucun écran ne puisse dire pourquoi. Le correctif prend `READ or WRITE` — mais une
permission persistante **ne se complète pas après coup** : un dossier ajouté avant la 4.6 reste
en lecture seule tant qu'il n'a pas été rechoisi. D'où `Depot.racinesEnLectureSeule()`, la ligne
« Autoriser l'écriture » dans *Dossiers de musique*, et le message qui nomme la cause au lieu de
constater l'échec.

`resumeAnalyse` donne le bilan affiché en fin d'analyse — y compris « Aucune modification », qui
est une réponse à part entière : sans elle, une analyse sans effet ressemble à une analyse qui
n'a pas eu lieu.

`retirerLesPlaylistsDisparues` retire une playlist quand son fichier est **absent de l'analyse**
et **illisible**. ⚠️ Les deux conditions comptent : sans la seconde, une playlist rangée dans un
dossier qu'on ne surveille plus disparaîtrait alors que son fichier est toujours là. Les
playlists créées dans l'application n'ont pas de `source` : elles ne sont jamais concernées.

« Nouvelle playlist » passe par `CreateDocument` : le sélecteur d'Android demande le dossier
**et** le nom, donc il n'y a rien à saisir à la molette. Le partage passe par `FileProvider`
(`${applicationId}.partage`, cache uniquement) et `ACTION_SEND_MULTIPLE`.

## Réécrire le `.m3u` d'origine (4.7)

`repercuterDansLeFichier` **rend compte** (`ResultatReecriture`) au lieu d'avaler son échec dans
un `runCatching`. ⚠️ C'est le vrai défaut de fond : la playlist changeait dans l'application, le
fichier restait tel quel, et rien ne le disait — il a fallu plusieurs versions pour s'en
apercevoir. Le résultat remonte jusqu'au message d'ajout, et un refus d'écriture ouvre une
proposition d'autoriser l'écriture sur-le-champ.

⚠️ **Les chemins écrits sont relatifs au dossier du fichier** (`cheminRelatif`), jamais à la
racine surveillée : un `.m3u` rangé dans `Music/Playlists` qui annonce `Music/FR/x.flac` fait
chercher `Music/Playlists/Music/FR/x.flac`. Le fichier est toujours réécrit en `#EXTM3U` complet,
quel qu'ait été son format d'origine.

⚠️ **Un sous-dossier d'un dossier surveillé ne s'ajoute pas** (`contient`, `ResultatRacine`) : le
28/09/2026, choisir `Music/Playlists` alors que `Music` était surveillé a fait passer la
bibliothèque de 14 à 27 playlists. Le sélecteur de réautorisation s'ouvre désormais **sur le
dossier concerné** (`EXTRA_INITIAL_URI` via `choisirDossier(uri)`), et `racinesEnDouble()` signale
ceux qui se chevauchent déjà.

## Exports et `.m3u8` (4.8)

⚠️ **Un dossier d'export n'est pas relu.** Exporter dans un dossier surveillé y écrit des copies
des playlists ; à l'analyse suivante, elles revenaient comme de nouvelles playlists et tout
apparaissait en double (signalé le 28/09/2026 : 14 playlists devenues 28). Les destinations
d'export sont donc retenues (`EtatReglages.dossiersExportes`) et `importerPlaylists` les ignore.
**Sauf** si la destination est elle-même un dossier surveillé : l'utilisateur a alors demandé
explicitement qu'on la lise. Les copies déjà importées sont retirées au moment de l'export
(`oublierLesPlaylistsDe`), ce qui répare aussi les bibliothèques déjà encombrées.

Une playlist réécrite est renommée en **`.m3u8`** (`nomEnM3u8`, `DocumentsContract.renameDocument`) :
36Music écrit toujours en UTF-8, et un `.m3u` est censé ne pas l'être — les accents ressortaient
en charabia ailleurs. ⚠️ Le renommage change l'URI du document : `marquerSource` met la playlist
à jour dans la foulée, sinon elle perdrait son fichier. Un échec de renommage n'est pas fatal :
on garde l'ancienne URI, qui reste valable.

## Le ménage des playlists (4.9)

La liste des playlists doit correspondre aux **fichiers présents**. `deciderLeMenage` est une
fonction pure — `Depot.nettoyerLesPlaylists` ne fait qu'exécuter sa décision — et s'applique après
chaque analyse, ou à la demande (*Paramètres → Playlists → Retirer les doublons*).

Deux règles, dans cet ordre :

1. **Un fichier, une playlist.** Plusieurs lignes ont pu désigner le même document : le
   28/09/2026, renommer `X.m3u` en `X.m3u8` alors qu'une copie exportée portait déjà ce nom a fait
   tomber les deux sur le même fichier (vingt-huit lignes pour quatorze fichiers, relevé dans la
   base du téléphone). ⚠️ On garde **la plus fournie** — c'est elle qui porte les ajouts faits
   dans l'application — et **on réécrit son fichier avant** de retirer les autres, jamais après.
2. **Plus de playlist sans fichier.** Ce qui n'existe plus sur l'appareil quitte la bibliothèque.
   Une playlist créée dans l'application n'a pas de `source` : elle n'est jamais concernée.

⚠️ `convertirEnM3u8` refuse désormais de renommer si un document du nom visé existe déjà à côté :
le fournisseur d'Android rend alors le document existant, et c'est ainsi que deux playlists
fusionnent.

## Un fichier, une playlist (5.0)

⚠️ **Un document se lit par autant d'URI qu'il existe d'arbres qui le contiennent.** Le même
`.m3u8` atteint par `tree/…:Music` et par `tree/…:Music%2FPlaylists` donne deux URI différentes
pour un seul fichier ; les comparer entières le fait passer pour deux, et la playlist entre deux
fois. Seul l'identifiant de document compte : `cleDeFichier` sert de clé partout — à l'import
(`importerPlaylists`) comme au ménage (`deciderLeMenage`).

⚠️ Et dans **une même analyse**, le fichier est rencontré une fois par dossier surveillé : la
liste des fichiers est donc dédoublonnée (`distinctBy`) et la carte des playlists connues est
**tenue à jour au fil de l'import**. Avec une carte figée au départ, la playlist créée à la
première rencontre était inconnue à la seconde — c'est ce qui a fait renaître une paire après un
ménage pourtant complet (journal du 28/09/2026 à l'appui).

## Recherche et tri (5.1)

**La loupe** est dans la barre de titre de tous les menus sauf « Lecture en cours », Cover Flow
(un carrousel, pas un menu) et les confirmations. Elle ouvre un champ et le clavier : ⚠️ c'est la
seule saisie au doigt des menus, parce que composer « rat*emi » à la molette serait un supplice.
Une fois le clavier rangé, la molette reprend la main dans les résultats. MENU ferme d'abord la
recherche, puis seulement remonte d'un niveau ; changer de menu la referme.

⚠️ **Le filtre (`filtrer` dans `EcranRetro`) s'applique aux quatre endroits qui construisent la
liste** : affichage, molette, bouton central, appui long. Si un seul voyait la liste entière, la
ligne surlignée et celle qu'on ouvre ne seraient plus les mêmes.

`correspond(motif, texte)` (`data/RechercheEtTri.kt`, pure) : `emi*` commence par, `*emi` finit
par, `rat*emi` les deux, plusieurs étoiles dans l'ordre ; sans étoile, **contient** (une recherche
n'exige pas le nom exact). Casse et accents ignorés via `normaliser`. Le libellé **et** le détail
(l'artiste, sous un titre) sont interrogés.

**Le tri des playlists** (`TriPlaylist`) : un appui passe au suivant — Fichier → Titre → Artiste
→ Ajout → Fichier — sans menu. Retenu par playlist (`EtatReglages.trisPlaylists`, `id:ordinal`).
⚠️ La liste triée est **celle qu'on donne à jouer** : le titre suivant est celui qu'on voit sous
le curseur. « Date d'ajout » = entrée dans la bibliothèque, la plus récente en tête — une
playlist ne retient pas quand chaque titre y a été rangé.

## Fluidité de la recherche, et doublons dans une playlist (5.2)

⚠️ **Chaque cran de molette redemande la liste du menu.** Tout ce qui se calcule pour la
construire se paie donc à chaque cran. En 5.1, trois coûts s'y cumulaient pendant une recherche :
la playlist reconstruite et retriée en entier (elle n'était pas mémorisée), le filtre relancé sur
des milliers de noms, et `normaliser` qui recompilait son expression régulière à chaque nom. Les
trois sont corrigés : `entreesPlaylist` passe par `MemoRetro`, le filtre est mémorisé sur
(liste, motif), `MotifRecherche` prépare le motif une fois, et la regex des accents est une
constante. Mesuré sur le téléphone : dix crans enchaînés → dix lignes exactement, 90 % des images
sous 16 ms.

**Retirer une ligne d'une playlist** : `CibleAction.Titre` porte `playlistId` et `occurrence`
(le rang de l'apparition du titre, compté dans l'ordre du fichier par
`numeroterLesApparitions`). ⚠️ C'est ce rang, et non la position affichée, qui désigne la ligne :
la position change avec le tri, le rang jamais. `Depot.retirerOccurrence` ne retire que cette
apparition (`indexDeLApparition`) et réécrit le `.m3u`.

# L'analyse de la bibliothèque

À lire avant de toucher à `data/Analyseur.kt`.

## Pourquoi SAF et pas MediaStore

`MediaStore` aurait été plus rapide à écrire : une requête, une liste de titres, les pochettes
fournies. Il a été écarté pour trois raisons, dans cet ordre :

1. **La demande était explicite** : ne rien analyser au premier démarrage, laisser l'utilisateur
   désigner l'emplacement. `MediaStore` indexe tout le stockage partagé ; filtrer *après coup* sur
   un préfixe de chemin est un pansement — et les chemins réels d'un arbre SAF ne sont pas
   récupérables de façon fiable.
2. `MediaStore` exige `READ_MEDIA_AUDIO` (Android 13+) ou `READ_EXTERNAL_STORAGE`. Avec SAF,
   **l'application ne demande aucune permission de stockage** : l'autorisation porte sur le
   dossier choisi, et sur lui seul.
3. Ce que l'index du système ignore (dossier `Documents/`, carte SD mal indexée, fichier tout juste
   copié) reste lisible par SAF.

Le prix payé : il faut lire les métadonnées soi-même, fichier par fichier.

## Le parcours

`DocumentsContract.buildChildDocumentsUriUsingTree` + une requête par dossier. Deux points non
négociables :

- **Les sous-dossiers sont visités après avoir refermé le curseur** du dossier courant. Garder
  plusieurs curseurs SAF ouverts en descendant finit par épuiser le fournisseur de documents, et
  l'échec est silencieux : des dossiers manquent, sans erreur.
- Un dossier illisible est journalisé et sauté ; une racine dont l'autorisation a été révoquée est
  ignorée **sans supprimer ses pistes** — sinon un simple redémarrage après révocation viderait la
  bibliothèque.

## Ce qui n'est pas relu

Chaque piste garde `taille` et `dateFichier`. À l'analyse suivante, un fichier dont ni l'une ni
l'autre n'a changé n'est pas rouvert : une réanalyse d'une grosse bibliothèque coûte une simple
énumération. *Analyse complète* (dans les paramètres) force la relecture.

`dateAjout` est **repris de l'ancienne ligne** quand elle existe : sans cela, le tri « date
d'ajout » se réinitialiserait à chaque analyse.

## Les métadonnées

`MediaMetadataRetriever` pour les étiquettes, plus :

- **Android 12+** : `METADATA_KEY_SAMPLERATE` et `METADATA_KEY_BITS_PER_SAMPLE` ;
- **en dessous, ou si le retriever se tait** : `MediaExtractor` ouvre le conteneur et lit
  `sample-rate`, `channel-count`, `bit-rate` et l'encodage PCM. C'est ce qui permet d'afficher
  « 24 bits / 96 kHz » partout, et c'est ce que coupe le réglage *analyse approfondie*.

⚠️ **L'extension ne dit pas le codec.** Un `.m4a` peut abriter de l'AAC (avec perte) comme de
l'ALAC (sans perte) — deux formats que tout oppose. Ces conteneurs ambigus (`EXTENSIONS_AMBIGUES` :
m4a, mp4, mka, ogg, webm…) sont donc **toujours** ouverts pour lire le type MIME réel de la piste,
même quand l'analyse approfondie est éteinte. Sans cela, un ALAC s'annonce « AAC », perd son badge
Hi-Res et affiche un débit qui ne veut rien dire.

Quatre fichiers sont lus de front : au-delà, les décodeurs matériels se marchent dessus et le gain
disparaît.

## Les pochettes

1. Image embarquée dans le fichier → réduite à 720 px, compressée en JPEG, mise en cache dans
   `cacheDir/pochettes`, **une seule fois par album** (la clé mêle dossier, album et taille de
   l'image : vingt pistes d'un même disque ne stockent pas vingt jaquettes).
2. À défaut, une image posée dans le dossier — `cover`, `folder`, `front`, `albumart`… l'emportent,
   sinon la première trouvée. Elle est référencée par son URI SAF, sans recopie.

Le champ `Piste.pochette` est **toujours une URI** (`file://` pour le cache, `content://` pour une
image de dossier) : Coil charge les deux, et rien dans l'interface n'a à distinguer les cas.

## Les playlists trouvées

Un `.m3u` désigne ses pistes par des chemins de fichiers ; 36Music travaille sur des URI SAF. Le
rapprochement se fait par **nom de fichier**, les homonymes départagés par le nombre de segments
communs en fin de chemin (`M3u.partieCommune`) — c'est ce qui distingue `Live/01 Intro.mp3` de
`Studio/01 Intro.mp3`. Une playlist déjà importée est retrouvée par sa `source` et mise à jour au
lieu d'être dupliquée.

## ⚠️ Un fichier audio n'est jamais écarté

Si `MediaMetadataRetriever` refuse un fichier — cela arrive sur des M4A aux atomes inhabituels,
des fichiers tronqués, ou simplement volumineux —, la piste est **quand même** créée, nommée
d'après son fichier (`sansMetadonnees`). L'écarter la ferait disparaître de la bibliothèque alors
qu'ExoPlayer la lit peut-être très bien : ce sont deux moteurs différents, et celui qui lit les
étiquettes est le plus capricieux des deux.

Ces cas sont comptés (`EtatAnalyse.illisibles`) et annoncés dans les paramètres comme dans le menu
Réglages de la vue molette : « 3 fichiers gardés malgré des étiquettes illisibles ». Sans ce
décompte, le symptôme se résume à « il manque des morceaux », ce qui n'aide personne.

De même, le filtre de **durée minimale** ne s'applique pas à une durée **inconnue** (zéro) : une
durée qu'on n'a pas su lire n'est pas une durée courte.

## Ce qui survit à une réanalyse

Favoris, playlists et statistiques sont accrochés à **l'URI du fichier**, jamais à un identifiant
de ligne. Réinsérer une piste (`OnConflictStrategy.REPLACE` sur la même URI) rafraîchit ses
métadonnées sans rien détacher. `BaseTest` le vérifie explicitement — c'est la régression la plus
facile à introduire et la plus pénible pour l'utilisateur.

## Les listes de lecture ne sont pas de l'audio

`audio/x-mpegurl` **commence par `audio/`**. C'est par là qu'un `.m3u8` entrait dans la
bibliothèque : `estAudio()` testait l'extension puis, à défaut, le préfixe du type MIME — et le
fournisseur SAF annonce ce type-là pour une liste de lecture. Deux fichiers suffisaient.

Le dégât n'était pas cosmétique. Le nom `.m3u8` fait déduire à media3 un flux **HLS**, dont le
module n'est pas embarqué ; et comme ExoPlayer fabrique **toutes** ses sources dès la pose de la
file, l'élément fautif la faisait refuser entière :

```
IllegalStateException: ClassNotFoundException: androidx.media3.exoplayer.hls.HlsMediaSource$Factory
→ mediaItemCount = 0
```

« Toutes les chansons » était la seule vue à les contenir — un genre, un album, un artiste ou un
dossier les écartait — d'où une panne qui ne touchait **qu'elle**.

Trois verrous depuis :

1. `estAudio()` reconnaît les listes de lecture **avant** tout le reste, par extension *et* par
   type MIME (`MIMES_PLAYLIST`) ;
2. `Lecteur.element` pose **toujours** un type explicite (`MimeTypes.AUDIO_UNKNOWN` à défaut) :
   media3 ne déduit plus rien du nom de fichier, et la source reste progressive ;
3. `Depot.purgerLesListesDeLecture()` retire au démarrage celles qu'une version antérieure avait
   rangées, sans imposer de réanalyse.

## Le conteneur a le dernier mot sur les `.m4a`

Un `.m4a` abrite de l'AAC **ou** de l'ALAC — l'un avec perte, l'autre sans. `MediaExtractor`
devrait trancher, mais il échoue sur l'ALAC des appareils dépourvus du décodeur correspondant :
il retombe alors sur l'extension, et un fichier sans perte s'annonce « AAC » à 1 100 kbit/s, un
débit que l'AAC n'atteint pas.

`Mp4.codecAudio()` descend donc `moov > trak > mdia > minf > stbl > stsd` et lit le code à quatre
lettres de la première entrée — `alac`, `mp4a`, `fLaC`… Le conteneur ne dépend d'aucun décodeur
installé. Le gros `mdat` est **sauté**, jamais lu.

L'analyse étant incrémentale, un fichier inchangé n'est jamais relu : la correction n'atteindrait
donc les bibliothèques existantes qu'à la prochaine analyse complète. `corrigerCodecsMp4()` rouvre
au démarrage les seuls fichiers dont l'étiquette est contredite par leur propre débit
(`estUnAacInvraisemblable`) — sur une bibliothèque ordinaire, aucun.

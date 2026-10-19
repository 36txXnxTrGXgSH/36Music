# La chaîne audio, et ce qu'elle garantit vraiment

À lire avant de promettre quoi que ce soit sur la qualité sonore.

## Ce que 36Music garantit

Du fichier jusqu'à `AudioTrack`, l'application **n'ajoute aucune perte** :

| Étape | Ce qui est fait |
| --- | --- |
| Décodage | ExoPlayer, décodage exact des formats sans perte (FLAC, ALAC, WAV, AIFF) |
| Profondeur | **sortie 32 bits flottante** (`setEnableAudioFloatOutput`) : une source 24 ou 32 bits n'est pas tronquée à 16 |
| Fréquence | l'`AudioTrack` est ouvert **à la fréquence de la source** — l'application ne rééchantillonne jamais |
| Traitement | **aucun effet audio instancié** tant que l'égaliseur est éteint : ni égaliseur, ni correcteur de graves, ni gain |
| Volume | `player.volume` reste à 1,0 — le fondu, éteint par défaut, est la seule chose qui le touche |
| Normalisation | aucune : ni ReplayGain, ni compression, ni limiteur |
| Vitesse | confiée au matériel (`setEnableAudioTrackPlaybackParams`) ; à 1× rien n'est appliqué |

Autrement dit : **ce qui sort du décodeur est ce qui entre dans Android, bit pour bit.**

## Ce qu'aucune application ne peut garantir sur Android

Au-delà d'`AudioTrack`, le flux traverse **AudioFlinger**, le mixeur du système. Celui-ci ouvre la
sortie matérielle à une fréquence donnée (souvent 48 kHz sur le haut-parleur interne) et
**rééchantillonne** tout ce qui n'y correspond pas. Un FLAC 44,1 kHz peut donc être converti en
48 kHz par le système, sans que l'application en soit informée ni puisse s'y opposer.

Il n'existe **aucune API publique** pour forcer le HAL à suivre la fréquence de la source. Les
lecteurs qui annoncent du « bit-perfect » sur USB (USB Audio Player Pro et consorts) embarquent
leur **propre pilote USB Audio Class**, écrit sur `android.hardware.usb`, et court-circuitent
entièrement la pile audio d'Android. C'est un projet à part entière — gestion des points de
terminaison isochrones, horloges, tampons — et ce n'est pas ce que fait 36Music.

Bonne nouvelle : avec un **DAC USB**, Android sait ouvrir la sortie à la fréquence du DAC
(44,1 / 48 / 96 / 192 kHz selon le matériel). La chaîne est alors souvent transparente de bout en
bout — mais c'est le système qui le décide, pas l'application.

## Le DAC USB

`SortieMaterielle` surveille `AudioManager` (`registerAudioDeviceCallback`) et publie la sortie
active : type, nom du produit, fréquences, profondeurs et nombre de canaux **déclarés par le
matériel**. C'est ce qui s'affiche sous la qualité, dans le lecteur comme dans les paramètres :
« Casque USB-C · jusqu'à 32 bits / 384 kHz ».

Deux raisons d'aller au-delà du routage automatique d'Android :

1. **Le dire.** Android route le son vers le DAC dès qu'on le branche, mais ne l'annonce nulle
   part. Sans cette surveillance, impossible de savoir ce que la chaîne accepte.
2. **Le forcer.** Quand une enceinte Bluetooth reste appairée, la sortie peut lui revenir. Le
   réglage *Router vers le DAC USB* (allumé par défaut) appelle
   `ExoPlayer.setPreferredAudioDevice(dac)` et fixe la destination.

⚠️ On ne force **que** l'USB. Détourner le casque filaire ou le Bluetooth du routage système
n'apporterait rien et casserait les usages (appel entrant, navigation vocale). Réglage éteint, ou
DAC débranché : `setPreferredAudioDevice(null)` rend la main à Android.

Le déchargement audio (offload) n'a pas de sens sur un DAC USB — il vise le DSP du téléphone ; il
reste éteint par défaut, et l'égaliseur le désactive de toute façon.

## Aiguiller l'extracteur

Chaque `MediaItem` porte le **type MIME de son conteneur**, déduit de l'extension
(`typeConteneurDe` : `.m4a`, `.m4b`, `.mp4` → `audio/mp4`, `.mp3` → `audio/mpeg`…). Avec une URI
SAF, l'extension n'est pas toujours visible du lecteur, et le choix de l'extracteur repose alors
sur le seul flair du renifleur de contenu.

⚠️ Ce n'est qu'une **priorité**, jamais une exclusive : un fichier mal nommé reste lu par
l'extracteur qui le reconnaît. C'est pourquoi `typeConteneurDe` rend `null` dès qu'il y a un doute
(un `.aac` peut être de l'ADTS brut comme un conteneur) — une indication fausse vaudrait moins que
pas d'indication.

La recherche à débit constant est activée (`setConstantBitrateSeekingEnabled`, et son équivalent
ADTS) : sans elle, la barre de progression d'un MP3 ou d'un AAC brut sans index refuse de bouger.

## ⚠️ Le silence sans erreur

Quand **aucun décodeur de l'appareil** ne sait lire une piste, ExoPlayer **ne lève pas
d'exception** : il déroule l'horloge sur une piste qu'il n'a pas sélectionnée. Le morceau « se
lance », la position avance, et rien ne sort. Aucun message, aucun journal d'erreur.

C'est le cas de l'**ALAC** (Apple Lossless) sur beaucoup de téléphones : le conteneur `.m4a` est
parfaitement lu, le cookie de décodage est valide, mais il n'existe pas de décodeur `audio/alac`.

`Player.Listener.onTracksChanged` est le seul endroit où cela se voit :

```kotlin
if (pistes.containsType(TRACK_TYPE_AUDIO) && !pistes.isTypeSupported(TRACK_TYPE_AUDIO)) { … }
```

36Music y affiche alors « Aucun décodeur ALAC sur cet appareil ». Et pour savoir *quel* décodeur
travaille quand il y en a un, `onAudioDecoderInitialized` alimente `SortieAudio.decodeur`, affiché
dans la fiche du fichier — un morceau muet ne se distingue autrement pas d'un morceau sans
décodeur.

**Contournement** : pour l'ALAC, les décodeurs **logiciels** passent devant (`MediaCodecSelector`).
Certains décodeurs de constructeurs acceptent le format puis ne rendent que du silence. Pour tous
les autres codecs, l'ordre par défaut est conservé — il privilégie le matériel, donc la batterie.

**Depuis la 2.0, 36Music embarque son propre décodeur ALAC** (`lecture/alac/`), écrit en Kotlin :
les fichiers Apple Lossless se lisent désormais sur tous les téléphones, décodeur système ou non.
Voir la section suivante.

## Le décodeur ALAC embarqué

`lecture/alac/DecodeurAlac.kt` implémente Apple Lossless en Kotlin pur : décodage entropique de
Rice adaptatif, prédicteur linéaire à coefficients adaptatifs, démixage stéréo. `RendererAlac` le
présente à ExoPlayer comme n'importe quel décodeur logiciel, et `FabriqueRendus36` le place **en
tête** des moteurs de rendu — il ne répond que pour `audio/alac`, aucun autre format n'est
détourné.

⚠️ **Le piège qui coûte une journée** : l'historique du codeur entropique est tenu **multiplié par
512**. Le paramètre du code de Rice se calcule donc sur `historique >> 9`, jamais sur l'historique
brut. Avec l'oubli de ce décalage, le décodeur lit trop de bits, se désynchronise dès le deuxième
échantillon et rend du bruit blanc saturé — **sans jamais échouer franchement**.

⚠️ Second piège, du même genre : en stéréo, la largeur d'un canal est `profondeur - décalage + 1`.
Ce bit supplémentaire porte la différence entre les deux canaux ; l'oublier donne un décodage qui
« marche » mais sature.

### Comment on vérifie un décodeur sans perte sans avoir de référence

Aucun autre décodeur ALAC n'était installé sur le poste de développement. `DecodeurAlacTest`
s'appuie donc sur des propriétés que seul un décodage exact peut satisfaire :

- **les bits consommés** : à la fin d'une trame, le décodeur doit avoir lu ~100 % des octets de
  cette trame (au padding d'alignement près). C'est la mesure la plus sévère : une erreur d'un
  seul bit la fait dériver immédiatement — c'est elle qui a révélé le décalage manquant (192 %) ;
- **l'autocorrélation** du signal (> 0,8 pour de la musique, ~0 pour du bruit) ;
- **la continuité aux frontières de trames**, chaque trame se décodant indépendamment ;
- **le fichier entier**, trame par trame, avec le nombre total d'échantillons comparé à celui que
  le conteneur annonce (9 619 680 pour l'exemple).

Le test dépose aussi trente secondes de WAV dans `app/build/extrait-alac.wav` : les mesures
disent que c'est juste, l'oreille le confirme en dix secondes. Il s'abstient si le fichier
d'exemple n'est pas sur le poste.

### Ce qui reste à faire

La sortie est du **PCM 16 bits**. Un ALAC 24 bits est donc tronqué de ses huit derniers bits —
tout vaut mieux qu'un silence, mais c'est le seul endroit à reprendre pour du 24 bits de bout en
bout (`demixer` et `ecrireMono`). Les profondeurs 20 et 32 bits lèvent une exception explicite
plutôt que de rendre du bruit.

## Ce qu'affiche le lecteur

Trois informations, dans cet ordre, et il faut savoir laquelle dit quoi :

1. **la qualité décodée** (`AnalyticsListener.onAudioInputFormatChanged`) : ce que le décodeur
   produit réellement — c'est elle qui fait foi, pas les étiquettes du fichier ;
2. **le badge Hi-Res**, réservé aux formats sans perte au-delà de 16 bits / 48 kHz ;
3. **la sortie matérielle** et ce qu'elle accepte au maximum.

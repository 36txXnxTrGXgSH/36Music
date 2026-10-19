# Thèmes, palettes et contraste

À lire avant de toucher à `ui/Theme.kt` ou d'ajouter un écran qui ne vit pas dans un `Scaffold`.

## Le défaut de la 1.0, et sa règle

En 1.0, le lecteur plein écran était **illisible sur tous les thèmes sombres** : titre, durées et
icônes de transport sortaient en gris très foncé sur fond noir. Le thème clair, lui, était
impeccable — ce qui a mis du temps à être compris.

La cause n'était pas la palette mais **`LocalContentColor`**. Dans Material 3, un `Text` ou une
`Icon` sans couleur explicite prend la couleur de contenu du conteneur ; celle-ci est fournie par
`Surface`, et `Scaffold`, `Card`, `ListItem`, `AlertDialog` ou `ModalBottomSheet` en posent une.
`EcranLecteur` dessinait directement dans un `Box` : **hors de toute `Surface`, la valeur par
défaut de `LocalContentColor` est `Color.Black`**, quel que soit le thème.

Deux règles en découlent :

1. **Tout écran posé dans un `Box` nu fournit sa couleur de contenu** — `CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface)`
   ou une `Surface` englobante.
2. Un `Surface` dont la couleur est **modifiée par un alpha** (`surfaceVariant.copy(alpha = .7f)`)
   ne se reconnaît plus dans le thème : `contentColorFor` échoue et rend, là encore,
   `LocalContentColor`. Dans ce cas, passer `contentColor` explicitement.

Par sécurité, les textes et icônes notables du lecteur portent en plus leur teinte explicite.

## Dix-huit palettes, trois familles, zéro couleur écrite à la main

`PaletteApplication` compte dix-huit entrées réparties en trois familles (`FamillePalette`) :
**Classique** (teintes sobres, fonds presque neutres), **Vif** (couleurs franches, fonds
chaleureux) et **Néon** (accents électriques, fonds très colorés). La famille ne choisit pas les
teintes mais le **caractère** : saturation des accents et des fonds.

Chaque palette ne déclare que **deux teintes** (principale et secondaire, en degrés). Tout le
reste — fonds, conteneurs, contours, et surtout couleurs de texte — est calculé par `paletteDe`.

⚠️ **Ne pas revenir à des schémas écrits à la main.** Dix-huit palettes × deux modes × une
vingtaine de couleurs, cela ferait sept cents constantes dont personne ne vérifierait les
contrastes : c'est exactement ainsi qu'est né le défaut de lisibilité de la 1.0.

### Le calcul : viser la luminance, pas la clarté

`teinter(teinte, saturation, luminanceVisée)` cherche par dichotomie la clarté HSL qui atteint la
**luminance relative** demandée — celle du calcul de contraste WCAG.

C'est le point à comprendre : à clarté HSL égale, un jaune éclaire deux fois plus qu'un bleu.
Fixer la clarté donnerait des palettes lisibles pour certaines teintes et illisibles pour
d'autres. En visant la luminance, un « Citron vert » et un « Cobalt » ont le **même** contraste,
par construction.

Les luminances visées sont rassemblées dans l'objet `Cibles` : elles seules décident du contraste,
et il n'y a qu'elles à toucher pour rendre l'ensemble plus doux ou plus tranché.

### Choisir une palette

*Paramètres → Apparence → Palette de couleurs* ouvre un écran dédié (`EcranPalettes`), rangé par
famille. Chaque ligne est **dessinée avec les couleurs de la palette, sur son propre fond**, dans
le mode (clair ou sombre) réellement en cours : c'est le seul moyen de juger un thème sans
l'appliquer.

Les **couleurs dynamiques** (Material You, Android 12+) l'emportent sur la palette quand elles
sont allumées ; choisir une palette les éteint donc automatiquement, sinon le choix resterait sans
effet visible.

Le mode **AMOLED** n'est pas une palette de plus : c'est la palette sombre choisie, dont les seuls
fonds passent au noir (`enNoirAbsolu`). Les couleurs de texte ne sont pas touchées.

### La vue molette : une ambiance et une teinte

Depuis la 4.2, l'interface molette ne choisit plus une palette dans une liste : elle choisit une
**ambiance** (`ThemeCouleur`) puis une **teinte**, sur une barre de couleurs parcourue à la
molette (`ui/retro/TeinteRetro.kt`).

L'ambiance ne porte aucune couleur — seulement le caractère : `saturationAccent`,
`saturationFond`, `clarteAccent` et `ecartSecondaire`, l'écart en degrés jusqu'au second accent.
C'est ce qui fait qu'un « Néon » reste un néon et un « Pastel » un pastel, quelle que soit la
couleur retenue. Les vingt-trois ambiances sont rangées en six catégories
(`CategoriePalette`) : trois étages de menu plutôt qu'une liste de vingt-trois lignes qui se
ressembleraient toutes.

`paletteDe(theme, teinte, sombre)` et `paletteDe(palette, sombre)` appellent le **même**
constructeur : il n'y a qu'un seul calcul de couleurs dans l'application, et donc un seul endroit
où le contraste se joue.

⚠️ Deux choix de conception valent d'être gardés :

- la teinte en cours d'essai **ne quitte pas l'écran** tant que le bouton central n'est pas
  pressé. Écrire dans DataStore à chaque cran ferait une dizaine d'écritures par seconde, et
  MENU ne pourrait plus rendre les couleurs d'avant ;
- les bandes de la barre sont peintes avec l'accent **réellement calculé** pour chaque teinte, et
  non avec un arc-en-ciel décoratif. Sur une ambiance peu saturée, la barre est donc presque
  grise — ce qui est exactement ce que l'on obtiendra.

Le garde-fou change de nature avec ce réglage : la teinte n'étant plus écrite dans le code,
vérifier les ambiances livrées ne suffit plus. `ThemesCouleurTest` mesure chacune d'elles **sur
tout le tour du cercle**, de trente en trente degrés, en clair, en sombre et en AMOLED.

## Le garde-fou

`ContrasteTest` mesure, pour chaque palette et chaque mode (clair, sombre, AMOLED), les rapports
de contraste WCAG :

| Couple | Seuil |
| --- | --- |
| `onSurface` / `surface`, `onBackground` / `background` | 4,5 |
| `onSurface` / `surfaceContainerHigh` (barre de lecture) | 4,5 |
| `onSurfaceVariant` / `surface` et `/ surfaceVariant` | 3,0 |
| `primary` / `surface`, `error` / `surface` | 3,0 |
| `onPrimary` / `primary` | 4,0 |

Un écran mal contrasté ne fait échouer aucun test d'affichage : il faut mesurer les couleurs
elles-mêmes. Les **dix-huit palettes** y passent dans les trois modes, soit cinquante-quatre jeux
de couleurs vérifiés à chaque exécution ; ajouter une palette, c'est ajouter une ligne à l'enum et
laisser le test juger. Le noir sur `#101418` du défaut d'origine y obtient 1,14 et serait refusé.

Les couleurs dynamiques ne sont pas mesurables ici : elles viennent du fond d'écran de
l'utilisateur, et c'est Android qui garantit leurs contrastes.

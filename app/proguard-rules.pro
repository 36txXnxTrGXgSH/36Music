# Room, media3 et Compose apportent leurs propres règles ; il n'y a ici que ce qui manque.

# Les entités Room sont instanciées par réflexion depuis le code engendré.
-keep class com.music36.app.data.Piste { *; }
-keep class com.music36.app.data.Racine { *; }
-keep class com.music36.app.data.Favori { *; }
-keep class com.music36.app.data.Playlist { *; }
-keep class com.music36.app.data.PlaylistPiste { *; }
-keep class com.music36.app.data.Statistique { *; }
-keep class com.music36.app.data.Empreinte { *; }

# Le service de lecture est désigné par son nom dans le manifeste et par les contrôleurs media3.
-keep class com.music36.app.lecture.ServiceLecture { *; }

# Les effets audio du système sont instanciés par réflexion sur certains téléphones.
-keep class android.media.audiofx.** { *; }

# XDebt — mises à jour dans l'application

## Fonctionnement
- À l'ouverture, l'application interroge `GET https://api.github.com/repos/Chasmet/XDebt/releases/latest` si l'option de vérification automatique est activée.
- Si une version **stable** plus récente existe et contient un APK `XDebt-*.apk`, XDebt propose de le télécharger.
- Android DownloadManager gère la progression en notification et conserve le téléchargement si l'application est interrompue.
- Avant l'installation, l'application vérifie :
  - le même package Android : `com.chasmet.xdebt` ;
  - un `versionCode` supérieur ;
  - **la même signature cryptographique** que l'application déjà installée.
- Android demande la confirmation d'installation. XDebt n'exécute ni désinstallation ni effacement des données.
- Si Android demande l'autorisation « Installer des applications inconnues », l'utilisateur doit la confirmer. Cela ne peut pas être forcé en silence.
- Les installations depuis Google Play recevront les mises à jour par Play Store.

## Point essentiel : signature permanente
Le build GitHub Actions crée toujours l'APK debug pour les tests. Les builds debug GitHub Actions peuvent utiliser **des clés de debug différentes** d'une exécution à l'autre : ils NE sont PAS garantis compatibles en mise à jour, même avec le même nom de package.

Les mises à jour fiables nécessitent une première installation depuis un APK **release signé par la clé permanente**, puis l'utilisation de **la même clé à chaque build**.

Configurer les quatre secrets GitHub Actions du dépôt XDebt :
- `XDEBT_KEYSTORE_B64` : base64 du fichier JKS binaire (ne jamais committer la clé).
- `XDEBT_STORE_PASSWORD` : mot de passe JKS.
- `XDEBT_KEY_ALIAS` : alias de la clé.
- `XDEBT_KEY_PASSWORD` : mot de passe de la clé.

Le workflow doit pouvoir créer une Release `v1.0.X` incluant `XDebt-v1.0.X.apk` signée (plus AAB et SHA256). Sans ces secrets, il ne publie **aucune Release stable**. Vérifier le certificat sur la Release.

Si XDebt V1.0.7 debug est déjà installé, et que son certificat n'est pas le même que celui de la première release signée, Android **refusera** la mise à jour sur place. Ne désinstallez pas l'application sans sauvegarder les éventuelles données locales. Cette limite ne se contourne pas en code.

## Diagnostic serveur de comptes
Le code Android n'installe pas les mises à jour du serveur PC. Le bouton **Tester la connexion XDebt** appelle `/xdebt/api/health`. Si la réponse est **404**, l'installation de CHK Universal Control en fonctionnement est une ancienne version ou pointe sur une autre cible HTTP.

Les API d'inscription, profils et factures sont des modules `/xdebt/api/*` du **serveur CHK Universal Control unique**. Aucun autre serveur n'est nécessaire. Le téléphone doit attendre que le même serveur Windows soit mis à jour et redémarré.

## Validation
Compilation automatique `./gradlew assembleDebug`, manifeste et FileProvider. Tester sur téléphone avant Google Play, avec une première release signée et une seconde release signée avec la même clé.

# SkillMajAuto — XDebt Android

Cette application contient maintenant la même famille de fonctionnalités de mise à jour que CHK Paiements, adaptée à XDebt.

## Dans l'application
- Paramètres : section SkillMajAuto, version courante, option de surveillance et bouton **SkillMajAuto · Vérifier les mises à jour**.
- À l'ouverture : contrôle GitHub Releases si l'option est activée.
- En arrière-plan : contrôle périodique **WorkManager (12 h au mieux, non garanti à l'heure exacte)**. Une notification apparaît lorsqu'une version stable est détectée, si l'autorisation de notifications est accordée.
- Lorsqu'une version existe : téléchargement avec Android DownloadManager, notification lorsque l'APK compatible est récupéré, puis confirmation de l'installation dans XDebt.
- Pendant l'installation : vérification du package Android, du versionCode croissant et de la signature exacte. L'installateur Android demande confirmation; aucune désinstallation ni suppression des données.

## Une Release officielle est indispensable
L'APK stable doit s'appeler exactement `XDebt-X.Y.Z.apk`, attaché à la Release GitHub `vX.Y.Z`, dans le dépôt `Chasmet/XDebt`. La CI crée automatiquement cet asset **uniquement lorsque les quatre secrets permanents sont configurés** :

- XDEBT_KEYSTORE_B64
- XDEBT_STORE_PASSWORD
- XDEBT_KEY_ALIAS
- XDEBT_KEY_PASSWORD

Ne jamais committer ou envoyer la clé privée en clair dans des notes, tickets ou messages publics. Si une signature différente est utilisée, Android refusera l'installation par-dessus l'existante — les données sont préservées et le système ne désinstalle rien.

## Ancien XDebt / XDebt (Test)
Les APKs debug préalablement distribués, notamment celui affiché comme « XDebt » dans les anciennes captures, étaient signés par une clé de test GitHub Actions non pérenne. Leurs certificats ne sont pas réutilisables avec garantie. Le nouvel APK de test porte l'identifiant `com.chasmet.xdebt.dev` et s'installe séparément, sans toucher à l'application précédente. **La vérification automatique des mises à jour est inactive en mode Test**, car elle ne peut garantir l'installation par-dessus une ancienne version debug.

La première version XDebt officielle signée de manière permanente inaugurera les mises à jour en place. Pour la version diffusée par Google Play, il faudra passer au mécanisme officiel Google Play In-App Updates : la permission `REQUEST_INSTALL_PACKAGES` peut être incompatible avec ses règles de publication.

## Adresse serveur CHK Universal Control
La saisie `https://chk-universal-1.taild9e4de.ts.net/mcp` est normalisée automatiquement vers `https://chk-universal-1.taild9e4de.ts.net`. Une application doit contacter `/xdebt/api/*` et non `/mcp/xdebt/api/*`.

Si `/xdebt/api/health` répond 404, la version active du serveur Windows n'a pas encore chargé XDebt. Le mécanisme **SkillMajAuto Android ne met pas à jour le processus serveur Windows**.

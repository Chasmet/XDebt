# XDebt — Android (Java)

XDebt est une application gratuite de présentation publique de profils et de classement de factures. **Les paiements, transferts et dons réels ne sont pas activés en V1.**

## Fonctionnalités V1
- Compte utilisateur avec mot de passe et profil public, photo de profil facultative.
- Neuf catégories : Électricité, Eau, Gaz, Maison/Loyer, Internet/Téléphone, Santé, Transport/Auto, Éducation, Crédit/Dettes.
- Trois catégories personnalisées maximum.
- Factures (titre, montant, échéance), **privées par défaut** ; publication explicite possible.
- Liste des profils publics et consultation de leurs seules factures publiées.
- Configuration de l'URL HTTPS du serveur.
- Recherche des Releases GitHub pour les futures mises à jour, publication Play Store après validation.

## Server
La branche PR `feature/xdebt-isolated-api` de `Chasmet/CHK-serveur-Universal-Control` fournit `/xdebt/api/*`. Tant que cette branche n'est pas validée et déployée, les écrans connectés resteront indisponibles. Ne pas saisir de factures personnelles dans une instance non approuvée.

## Compiler
Pré-requis : JDK 17, Android SDK Platform 34, Gradle 8.7.
```bash
./gradlew assembleDebug
```
GitHub Actions installe les outils et compile automatiquement. La première exécution génère le wrapper Gradle complet (`gradle-wrapper.jar` inclus), puis le versionne.

## Signer les Releases GitHub
Secrets GitHub requis : `XDEBT_KEYSTORE_B64`, `XDEBT_STORE_PASSWORD`, `XDEBT_KEY_ALIAS`, `XDEBT_KEY_PASSWORD`. Utiliser **la même clé** pour toutes les versions ; conserver une copie chiffrée hors GitHub. Sans ces secrets, seul l'APK debug est produit.

La sortie `app-release.aab` est destinée à Google Play après revue; les mises à jour des utilisateurs Play doivent utiliser Google Play, et non une installation APK externe.

## À faire avant Google Play
Contrôle anti-fraude, vérification d'identité, gestion du RGPD et des demandes de suppression, politique de confidentialité, récupération de compte, procédure de signalement, conformité paiement/dons avec un prestataire agréé, revue sécurité indépendante et politique Play.

# Embedded call activation dependencies

Used by the optional integrated call-recording activation module. No Shizuku manager application is embedded or required.

- LibADB Android 3.1.1, copyright Muntashir Al-Islam. Apache-2.0 option selected. Source and build: https://github.com/MuntashirAkon/libadb-android/tree/3.1.1 . Full Apache licence included. Its source files also contain BSD/MIT attributions which remain in upstream and sources.
- SPAKE2 Java/Android 2.2.1, copyright Muntashir Al-Islam. LGPL-3.0. Corresponding source and rebuild instructions: https://github.com/MuntashirAkon/spake2-java/tree/2.2.1 . Modified/simplified EdDSA Java implementation credited upstream under CC0: https://github.com/str4d/ed25519-java . LGPL text included; this application's GPL-3.0 source/build scripts enable rebuilding and relinking the dependency. No modifications made to these library binaries.
- Conscrypt Android 2.5.3, Android Open Source Project and contributors. Apache-2.0, with bundled upstream notices for its components. Source: https://github.com/google/conscrypt/tree/2.5.3 . Licence/NOTICE included. Host-only tests use conscrypt-openjdk-uber 2.5.2 and do not package it in the Android APK.
- Bouncy Castle bcprov/bcpkix/bcutil jdk15to18 1.81, Legion of the Bouncy Castle contributors. Bouncy Castle permissive licence included. Source: https://github.com/bcgit/bc-java/tree/r1rv81 . Used to generate the local pairing certificate; no application-wide crypto provider replacement.

All pairing and helper code in dot.dialer is built into the APK. No code is downloaded or replaced during activation. Pairing identities are generated per installation and are never shared through builds or backups.
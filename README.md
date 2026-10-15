<div align="center">

# Dot Dialer

An open-source Android dialer focused on reliable call lifecycle handling, fast contact search,
accessibility and restrained, system-consistent haptics.

</div>

Internal product and engineering documentation is maintained privately and is not included
in this repository.

## Build and verify

Requirements: JDK 21 (the source targets Java 17), and an Android SDK containing platform 37.0.
Set `ANDROID_HOME` to your SDK directory, or set `sdk.dir` in an untracked `local.properties`.

```shell
./gradlew --no-daemon :app:assembleFossDebug
./gradlew --no-daemon qualityGate
```

The source tree includes the build configuration, automated checks, and branding inputs needed
by the quality gate. Third-party license notices remain available under `LICENSES/`.
FOSS builds require no Google account credentials, billing keys or production signing keys.
Debug builds use Android's generated debug key when no local key exists; release builds are
unsigned without signing credentials. The other distribution source sets are retained so that
the complete application source remains available; `foss` selects the FOSS implementation.

## Upstream and license

Dot Dialer is based on [Goodwy/RPhone](https://github.com/Goodwy/RPhone). The upstream
copyright notices are retained; details are in [UPSTREAM.md](UPSTREAM.md).

The source is licensed under [GNU GPL version 3](LICENSE). Selling binaries is permitted by the
license, while recipients retain the GPL rights to the corresponding source.

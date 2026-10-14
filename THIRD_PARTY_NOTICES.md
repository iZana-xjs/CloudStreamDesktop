# Third-party notices

This file records third-party assets identified in the current source. It is not yet a complete notice for every library in the Windows distribution; see `docs/RELEASE_AUDIT.md` for the remaining dependency audit.

## CloudStream

This project contains code derived from the CloudStream project. See the root `LICENSE` and `docs/upstream-cloudstream/README.android-upstream.md` for the upstream license and attribution. Desktop modifications are maintained in this repository.

## Open Sans

Open Sans is bundled in the Android source for subtitle and UI font use. The font file metadata identifies Apache License 2.0. The license text is in `licenses/fonts/Apache-2.0.txt`.

## Poppins

Poppins is bundled in the Android source for subtitle font use and is licensed under the SIL Open Font License 1.1. The license text is in `licenses/fonts/OFL-1.1.txt`.

## The Movie Database (TMDB)

CloudStream Desktop uses the TMDB API for movie and TV metadata. The approved TMDB logo is bundled unmodified in `shared/src/commonMain/composeResources/drawable/tmdb-logo.svg` and shown in the app's About section. This product uses the TMDB API but is not endorsed or certified by TMDB. API and imagery use remains subject to TMDB's current terms.

## Other dependencies and assets

The Windows package also contains third-party software, including Compose Multiplatform, Kotlin, LibVLC/VLCJ, dex2jar, ASM, Coil, and other dependencies. Their exact versions, licenses, required notices, and binary redistribution permissions must be inventoried and included before public MSI distribution.

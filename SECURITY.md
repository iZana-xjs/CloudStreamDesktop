# Security notes

CloudStream Desktop is still under development and has not had a professional security audit. Do not use this document as a claim that the application is secure.

## Extensions are trusted code

Extensions are downloaded as `.cs3` archives, converted, and loaded into the application's JVM. They are not sandboxed. An extension can act with the same operating-system permissions as the logged-in user through the app process. Use only repositories and extensions you trust; remove any extension you do not recognize.

The desktop extension manager now requires HTTPS for repository and plugin URLs, rejects credential-bearing URLs and unsafe extension identifiers, enforces a 100 MB download limit, validates the archive structure, and stops installation when a supplied SHA-256 checksum is invalid or does not match. These checks reduce transport, path, and malformed-download risks. They do not prove that an extension is safe or authentic when its repository provides no checksum.

## Data and network use

App settings, history, bookmarks, and extension files are stored locally in the user's app-data directory. Searches and playback requests may be sent to the metadata service, the selected extension repository, and the extension's provider sites. Review the privacy terms of the services and providers you choose.

Do not put passwords, signing keys, private tokens, or personal app data in source control. Build signing values must be supplied outside tracked files. If a real secret was ever committed, revoke or rotate it; deleting it in a later commit does not remove it from Git history.

## Reporting a vulnerability

Until a public repository and a private reporting channel are configured, do not publish exploit details or personal information in an issue. Before public release, enable GitHub private vulnerability reporting and document the reporting route here.

# Pre-publication copyright and security audit

Status: preliminary audit for a private GitHub development repository. This is not legal advice and is not clearance for a public release or binary redistribution.

## Copyright and license checks

- The project identifies itself as derived from the CloudStream Android project and includes the upstream GPL-3.0 license and Android README attribution. Preserve upstream copyright/license notices and clearly mark desktop modifications and dates before publishing a derivative build.
- The repository's top-level GPL license does not automatically establish redistribution rights for every separately licensed asset or dependency. Review the license and provenance of every bundled font, image, icon, codec/runtime, and library before publishing source or MSI files.
- Font files in `app/src/main/res/font/` and `shared/src/commonMain/composeResources/font/` are a specific unresolved review item. Names such as Netflix Sans, Gotham, and product-specific fonts require proof of a compatible redistribution license or replacement. Determine which are actually packaged in the Windows build.
- TMDB and Simkl API integrations and branding must be checked against their current terms before a public release. Client identifiers embedded in a desktop app can be extracted; do not treat them as private secrets. No personal account credentials were found in the source scan.
- Repository presets point users to third-party extension ecosystems. The app should not imply that those providers or the content they expose are endorsed or licensed by this project. Reassess default repository presets and public-facing claims before public release.
- Generate a dependency and asset license inventory for the actual Windows MSI, retain required notices, and verify that each item permits the intended distribution. This remains a release blocker.

## Security checks and known limits

- Build output, local Gradle state, local properties, keystores, recordings, and app-data directories are ignored; no personal credentials or local app data were found among the staged source files in this audit.
- Extension URL and identifier validation, bounded archive downloads, archive structure checks, and strict validation of repository-provided SHA-256 values were added to the desktop extension manager. Unit tests cover the URL and identifier rules.
- Extensions execute in-process without a sandbox. HTTPS, size checks, and hashes do not make third-party extension code trustworthy. The UI and `SECURITY.md` now explain this trust boundary.
- The project has not had a professional security review. Keep the first GitHub repository private; do not publish an MSI or switch it to public until the font/asset and dependency audit, test/build checks, privacy review, and vulnerability reporting setup are complete.

## Before making the repository public

1. Confirm every source and asset license, replace or remove any unlicensed fonts/assets, and publish complete third-party notices.
2. Review TMDB/Simkl terms, API key exposure, repository presets, and privacy disclosures.
3. Run the full test suite, inspect the packaged MSI contents, and perform a clean-machine installation check.
4. Enable GitHub secret scanning/push protection, Dependabot, and private vulnerability reporting where available; add the reporting contact here.
5. Review the complete Git history and repository contents for secrets, personal data, build artifacts, and copyrighted files without redistribution permission.

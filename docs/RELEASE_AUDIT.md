# Pre-publication copyright and security audit

Status: preliminary audit for a private GitHub development repository. This is not legal advice and is not clearance for a public release or binary redistribution.

## Copyright and license checks

- The project identifies itself as derived from the CloudStream Android project and includes the upstream GPL-3.0 license and Android README attribution. Preserve upstream copyright/license notices and clearly mark desktop modifications and dates before publishing a derivative build.
- The repository's top-level GPL license does not automatically establish redistribution rights for every separately licensed asset or dependency. Review the license and provenance of every bundled font, image, icon, codec/runtime, and library before publishing source or MSI files.
- The initial audit found the Windows MSI bundled Google Product Sans, and the Android source contained font files whose metadata identifies Microsoft, Netflix, and commercial font foundries. Those unverified font files have now been removed. The desktop theme uses the platform default typeface; Android source font choices are limited to Open Sans and Poppins, with license texts in `licenses/fonts/`. Previously saved Android subtitle-font selections map to Open Sans for compatibility.
- The Windows About screen now displays the approved TMDB logo and required API attribution notice. This covers attribution only; review the current API terms and obtain commercial permission if the project becomes commercial.
- `THIRD_PARTY_NOTICES.md` documents identified fonts, TMDB, and CloudStream. It explicitly records that the Windows dependency license and binary redistribution inventory is still incomplete; this remains a public MSI release blocker.
- TMDB attribution is now present, but TMDB and Simkl API integrations and branding must still be checked against their current terms before a public release. Client identifiers embedded in a desktop app can be extracted; do not treat them as private secrets. No personal account credentials were found in the source scan.
- A follow-up source scan surfaced hard-coded API identifiers/keys in inherited integrations (including TMDB, OpenSubtitles, and an extractor). Their exact authorization and redistribution terms must be confirmed before public release. Treat every value shipped in a client as extractable; revoke/rotate any value that is actually a private credential.
- Repository presets point users to third-party extension ecosystems. The app should not imply that those providers or the content they expose are endorsed or licensed by this project. Reassess default repository presets and public-facing claims before public release.
- Generate a dependency and asset license inventory for the actual Windows MSI, retain required notices, and verify that each item permits the intended distribution. This remains a release blocker.
- After security scanning was enabled, GitHub reported 73 open Dependabot alerts (4 critical, 32 high, 35 moderate, 2 low) on October 14, 2026. The alert set spans Android and build dependency graphs; not every alert is a desktop runtime dependency. The desktop runtime graph does include Jackson 2.13.1, which has open advisories. Do not distribute a public MSI until the runtime/build alerts are triaged and compatible fixes are verified. GitHub's critical alerts for FreeMarker, Bouncy Castle, and Netty were not present in the inspected desktop runtime or Android stable debug runtime graphs, but still need build-toolchain/source-set triage.
- Updated the direct Jsoup version from 1.22.1 to 1.23.2; `:desktopApp:dependencyInsight` confirms Gradle now selects 1.23.2 over the older transitive versions. Desktop tests and MSI packaging pass with this update. GitHub's alert totals may take time to refresh.

## Security checks and known limits

- Build output, local Gradle state, local properties, keystores, recordings, and app-data directories are ignored; no personal credentials or local app data were found among the staged source files in this audit.
- Extension URL and identifier validation, bounded archive downloads, archive structure checks, and strict validation of repository-provided SHA-256 values were added to the desktop extension manager. Unit tests cover the URL and identifier rules.
- Extensions execute in-process without a sandbox. HTTPS, size checks, and hashes do not make third-party extension code trustworthy. The UI and `SECURITY.md` now explain this trust boundary.
- The project has not had a professional security review. Keep the first GitHub repository private; do not publish an MSI or switch it to public until the font/asset and dependency audit, test/build checks, privacy review, and vulnerability reporting setup are complete.

## Before making the repository public

1. Confirm every source and asset license, replace or remove any unlicensed fonts/assets, and publish complete third-party notices.
2. Review TMDB/Simkl terms, API key exposure, repository presets, and privacy disclosures.
3. Run the full test suite, inspect the packaged MSI contents, and perform a clean-machine installation check.
4. Dependency graph, automatic dependency submission, Dependabot alerts/security updates, and malware alerts are enabled on the private GitHub repository. Weekly Gradle and GitHub Actions version-update checks are configured in `.github/dependabot.yml`. Secret scanning, push protection, and private vulnerability reporting controls were not available in this repository's current GitHub plan/settings; reassess availability before public release and add a reporting contact here.
5. Review the complete Git history and repository contents for secrets, personal data, build artifacts, and copyrighted files without redistribution permission.

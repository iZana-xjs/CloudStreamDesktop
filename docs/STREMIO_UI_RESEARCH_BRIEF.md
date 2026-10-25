# Stremio-Informed UI Refresh — Research Brief

Date: 2026-10-26
Project: CloudStream Desktop (Windows, Kotlin/Compose Desktop)
Purpose: Give OpenCode a clean-room, evidence-based direction for improving the app's visual quality and consistency. This is a design reference, not a request to reproduce Stremio.

## Evidence gathered

- Opened the installed Stremio Windows application and visually inspected its Board/home screen. Its initial welcome/add-on modal was dismissed to inspect the underlying screen.
- Used REA's static JavaScript analysis on a caller-selected set of 75 Stremio Web source files covering Board, Search, MetaDetails, navigation, search-bar, MetaRow, and MetaItem components. The evidence bundle and summary are stored outside the project at `D:\ToolCache\REA\stremio-ui-evidence.bundle.json` and `D:\ToolCache\REA\stremio-ui-summary.json`.
- REA's result is intentionally partial: 462 other inventoried files were outside the analysis selection; it reports static structure and candidate relationships, not runtime execution. The visible behavior was checked separately in the installed app.
- Inspected CloudStream's existing Compose UI and token definitions under `desktopApp/src/jvmMain/kotlin/com/lagradost/cloudstream4/ui/`.

## Stremio observations worth learning from

1. **Clear page hierarchy:** the Board uses concise section headings (for example, Popular and Featured), then image-led horizontal rows. A “See All” action is visually paired with its section heading, making the next action obvious.
2. **Persistent, compact navigation:** a narrow left navigation rail keeps the main destinations available without making the content compete with a wide header navigation strip.
3. **One obvious global search entry point:** search is prominent near the top center and visually distinct from page content.
4. **Artwork leads; labels follow:** posters are large, consistent portrait tiles with a readable title directly underneath. The visible row has space between tiles and avoids stacking several noisy badges on every poster.
5. **Restrained visual system:** a dark indigo canvas, limited purple accents, soft depth, and consistent rounded controls give the page one visual voice. The content and artwork carry most of the color.
6. **Separate responsibilities in source:** REA's selected source view confirms distinct Board, Search, MetaDetails, navigation, search-bar, and row/item component modules. CloudStream should keep its own Kotlin/Compose architecture while improving shared visual tokens and reusable components.

## CloudStream direction

The goal is a visibly more polished, deliberate, cinematic Windows app. Use Stremio only as one reference for hierarchy and navigation density. Preserve CloudStream's own brand and make original layouts and styling.

- Create a restrained palette with a deep neutral canvas, clearly differentiated surfaces, strong readable text, and one primary action color. Accent colors must have semantic meaning and should not compete for attention.
- Define consistent typography, spacing, card dimensions, radii, borders, hover/focus/pressed states, and transition timing in the existing Compose design system.
- Give the Home screen a strong but calm hierarchy: a well-proportioned featured area, clear section titles, aligned “View all” actions, consistent poster rails, and predictable spacing.
- Make navigation compact, legible, and balanced at both normal and maximized Windows sizes. Search, provider status, profile, and settings should not fight the navigation for attention.
- Use subtle depth and motion only where it helps orientation or feedback. Avoid permanent tilt, excessive scale/glow, busy gradients, arbitrary glass effects, fake ratings/quality claims, and decorative elements that reduce readability.
- Keep loading, empty, error, unavailable-artwork, hover, keyboard-focus, and narrow-window states intentional and visually consistent.
- Preserve CloudStream functionality, provider identity, search/history restoration, playback, downloads, watchlist, keyboard/mouse navigation, and persisted data. This visual phase is not a playback-engine or provider behavior rewrite.

## Copyright and license boundary

Stremio Web's public repository identifies its license as GPL-2.0-only; this project identifies its own license as GPL-3.0. Do not port, copy, translate, or adapt Stremio source code, styles, assets, icons, illustrations, poster art, exact screen compositions, or distinctive branding into CloudStream. Do not use REA output as a license to reproduce its implementation. Use only general product-design observations documented above and create original Compose code, styling, and assets. If any Stremio code or asset is ever proposed for reuse, stop and request a separate license review before incorporating it.

## Suggested phases

### Phase 1 — Shared visual foundation + Home + app shell

Implement only the shared design tokens/reusable visual primitives, the main desktop shell/navigation/search layout, and the Home screen/rails/cards. This is the first reviewable slice and sets the visual language for later work.

### Phase 2 — Search and result browsing

Apply the approved visual system to search entry, suggestions, provider filters, result cards, provider labels, loading/error states, and back-navigation restoration. Keep all existing result identity and filtering behavior.

### Phase 3 — Details, Discover, Extensions, and supporting screens

Apply the same system to media details, categories, extension/repository browsing, watchlist, downloads, history, and settings without changing provider semantics.

### Phase 4 — Player polish

Review the player controls and overlays separately, preserving fullscreen, tracks, seeking, skip markers, playback, and source-selection behavior. Do not bundle codec/renderer or playback-performance changes into a visual-only pass.

## Review criteria for Phase 1

- The app reads as one coherent product at 960x600, 1280x720, and a maximized desktop size.
- Navigation, search, provider/profile/settings actions have clear priority and remain usable without clipping or awkward empty gaps.
- Home sections have aligned headings/actions; posters share consistent sizing and title placement; text is legible and metadata is truthful.
- Hover and keyboard focus are obvious but restrained; animations do not move layout unexpectedly or make browsing feel busy.
- Existing routes/actions and content loading continue to work.
- The implementation compiles and the produced Windows MSI/distributable is generated on D:. The developer should report exact paths and any remaining visual checks for the user.
- After code changes, update `C:\Users\jelly\OneDrive\Desktop\project.txt` with what changed, build/verification result, what should be done next, and what remains open.

## Reference links

- REA project and methodology: https://github.com/morluto/rea
- REA CLI workflow: https://github.com/morluto/rea/blob/main/docs/cli.md
- Stremio Web source and its stated license: https://github.com/Stremio/stremio-web
- Stremio Windows shell release/source context: https://blog.stremio.com/stremio-v5-for-windows-is-now-open-source/

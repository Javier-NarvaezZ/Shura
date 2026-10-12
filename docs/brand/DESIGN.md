# Sunloop design tokens

The app's tokens, as they live in `:core:ui` (`theme/ShuraTokens.kt`). The app is dark only. The player and mini-player keep the cover tint (`ArtworkTint`, `ShuraOnTintColors`), unchanged by the brand.

## Color (`ShuraColors`)

| Token | Value | Use |
|---|---|---|
| `background` | `#120D0B` | Screens, window, splash |
| `surface1` | `#1E1714` | Raised areas (cards, sheets) |
| `surface2` | `#2A201C` | Fields, chips, placeholders, the cover-tint fallback |
| `surface3` | `#362A25` | The explicit-content mark, pressed states |
| `line` | `#FFF4EC` at 12 % | Dividers, outlines |
| `text` | `#FFF4EC` | Body and titles |
| `textMuted` | `#FFF4EC` at 66 % | Secondary text |
| `accent` | `#FF5E3A` (coral) | Current song, selected chips, switches, links, play buttons |
| `accentGold` | `#FFB547` (gold) | The second stop of the brand gradient |
| `onAccent` | `#120D0B` | Text and icons on coral, gold or the gradient |
| `error` | `#FF8FA3` (rose) | Errors; it stays distinct from the coral accent |

**Brand gradient** (`Shura.colors.brandGradient`): vertical, coral at the bottom and gold at the top, like the sun. Used only for the main "Reproducir" button on album, artist and playlist pages.

**Contrast** (WCAG 2.1; AA is 4.5 for normal text):

| Pair | Ratio |
|---|---|
| `text` on `background` / `surface1` / `surface2` / `surface3` | 17.83 / 16.33 / 14.68 / 12.81 |
| `textMuted` on `background` / `surface1` / `surface2` | 7.99 / 7.66 / 7.16 |
| `accent` on `background` / `surface1` / `surface2` | 6.35 / 5.82 / 5.23 |
| `accentGold` on `background` | 10.98 |
| `error` on `background` / `surface1` | 8.92 / 8.17 |
| `onAccent` on coral / gold / middle of the gradient | 6.35 / 10.98 / 8.24 |
| Light text on coral (not used) | 2.81, fails |

## Type (`ShuraTypography`)
Same scale as before (sizes and line heights unchanged). Only the family of the large roles changes.

| Role | Size / line height | Family |
|---|---|---|
| `displaySmall` | 32 / 38, Bold | Outfit |
| `headlineMedium` | 26 / 32, Bold | Outfit |
| `headlineSmall` | 22 / 28, Bold | Outfit |
| `titleLarge` | 20 / 26, SemiBold | Outfit |
| `titleMedium` and below | unchanged | System |

Outfit ships as the Outfit project's official static files: `Outfit-SemiBold.ttf` and `Outfit-Bold.ttf`, version 1.100, unmodified, with their `OFL.txt`.
- The fonts are Compose resources in `:core:ui` (`composeResources/font/`). Only the file names change, to valid resource names.
- The license ships with the app (`composeResources/files/licenses/`).

## Shapes, spacing, motion
Unchanged (`ShuraShapes`, `ShuraSpacing`, `ShuraMotion`).

## Android
- **Launcher:** adaptive icon (`mipmap-anydpi-v26`).
  - The foreground is the color mark inside the 66 dp safe zone.
  - The background is `@color/sunloop_background`.
  - The monochrome layer is for themed icons (Android 13 and later).
- **Notification:** small icon `media3_notification_small_icon`, a white silhouette of the mark, overriding Media3's default.
- **Window:** `#120D0B` background, light status-bar icons, and the Android 12 splash on the brand background.

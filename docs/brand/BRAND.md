# Sunloop brand

Sunloop is the public name. Shura stays as the internal code name, and the package does not change.

## Personality
Warm and close, bold and energetic: a friend who puts music on, not a corporate product.

**Voice** (the app speaks Spanish):
- **Short, warm, direct sentences:**
  - "Sigue con canciones parecidas", not "Activar funcionalidad de reproducción automática".
- **It explains what happened and what to do, without technical terms or apologies:**
  - "No se pudo cargar la canción. Revisa tu conexión e inténtalo de nuevo."
- **It never promises "sin anuncios" or "gratis de YouTube"**, and it does not use YouTube's or Google's names or marks as its own.

## Logo: "Atardecer sonoro"
A half sun setting on a horizon of two sound waves, which fade like the sun's reflection on water.

| File | Use |
|---|---|
| `sunloop-mark.svg` | Symbol in color (transparent background) |
| `sunloop-mark-tile.svg` | Symbol on the brand background, as an app tile |
| `sunloop-mark-mono.svg` | One color (`currentColor`) |
| `sunloop-wordmark.svg`, `-mono.svg` | The name alone |
| `sunloop-lockup.svg`, `-mono.svg` | Symbol and name side by side (preferred) |
| `sunloop-lockup-vertical.svg` | Symbol over name |
| `android/` | Adaptive icon layers and the notification icon |

**Construction** (on a 100 grid):
- **Sun:** a half disc 60 wide, sitting on the horizon at y = 52. It fades from coral at the horizon to gold at the top.
- **Waves:** two smooth lines.
  - The first is 60 wide, has 2.5 crests and is 10 thick.
  - The second is 38 wide, has 2 crests, is 9 thick and drawn at 82 % opacity.
  - Their ends are round.
- **The name:** Outfit 700 as outlines. In the lockup its cap height is 40 % of the symbol, and its baseline sits near the horizon.

**Clear space:** keep the height of the sun (30 on the grid) free around the lockup, and half of that around the symbol alone.

**Minimum size:**
- symbol: 24 dp, or 48 px for the launcher icon;
- lockup: 96 dp wide.

**Don't:**
- stretch or rotate it;
- recolor the sun or the waves outside the brand colors;
- put the color symbol on coral or gold (use the one-color version);
- add outlines or shadows;
- set the name in another typeface.

**Shape rule.** The mark always keeps:
- a dark field, never an orange background;
- a round half sun;
- horizontal waves that are smooth and rounded, with no peaks and no vertical bars.

## Color

| Role | Value |
|---|---|
| Background | `#120D0B` |
| Surface | `#1E1714` |
| Coral | `#FF5E3A` |
| Gold | `#FFB547` |
| Text | `#FFF4EC` |

- **Brand gradient:** coral → gold. It is the sun, and in the app only the main "Reproducir" button uses it.
- **Text on coral or on the gradient** is always the background color (`#120D0B`). Light text on coral fails WCAG AA (2.81).
- **Dark only:** the app has no light theme.
- **The player** keeps each cover's own tint.

## Type
- **Outfit**, SIL Open Font License 1.1:
  - the name, as outlines;
  - large titles in the app (album and artist names, section headings, the screen greeting), weights 600 and 700.
- **The system typeface** for everything else.
- See `DESIGN.md` and `docs/dependencies.md`.

## Sources
Every brand asset is drawn by hand as SVG. The generator scripts live outside the repository, and the Android drawables are generated from the same geometry. No third-party logos or images are used.

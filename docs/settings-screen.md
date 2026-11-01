# Settings screen layout

The main map settings screen uses one vertically scrolling list in Minecraft
1.20.1, 1.21.1, 26.1.2 and 26.2. Settings retain their existing values,
callbacks, validation and tooltips.

- Category headers use the existing translated names: Minimap, Markers,
  Fullscreen, Performance, Integrations and Deathpoints. Minecraft 26.1.2 has
  five categories because Smart Deathpoints has not been ported there.
- Each setting occupies its own row. Buttons and sliders keep Minecraft's
  standard 20 GUI pixel height; the list uses a wider, responsive single
  column instead of shrinking controls to fit the number of options.
- The title and the Import Waypoints / Done footer stay outside the scrolling
  viewport. Scrolling cannot cover them.
- Minecraft's native `ContainerObjectSelectionList` handles wheel scrolling,
  scrollbar dragging, clipping, keyboard focus and narration. Focused controls
  can be reached with the keyboard even when they start outside the viewport.
- Border color has a labelled direct-entry field and a color preview within
  Performance. The cheap death items field keeps a visible label. These
  fields still save through the existing responders.
- The same screen object keeps its scroll position when resized or reopened
  after a child settings screen.

This change introduces no configuration keys, file format changes, network
changes or dependencies. Existing category and setting translations are reused.
Version-specific options remain version-specific: this layout does not port
Smart Deathpoints, server settings, areas or controller hint settings to roots
which do not already implement them. Minecraft 1.21.11 has no supported source
root or distributable yet (fork register P07).

## Verification

Native loader builds check the four Minecraft API variants, including legacy
`render` and newer `extractContent` entry rendering. The focused
[headless regression](../scripts/test-settings-list.ps1) runs the actual 1.20.1
list helper against a minimal native-geometry fixture. It checks 320px / 640px
row widths, standard control height, scrollbar separation, color field / swatch
bounds and child exposure for native focus and narration. The fixture does not
implement native wheel scrolling or keyboard focus transitions; native API and
bytecode review cover their integration, while actual GPU appearance and
physical input remain unverified. The task record in
[the fork change register](fork-change-register.md) contains completed checks
and deployment evidence.

For an eventual in-game check, open map settings at the minimum GUI resolution
and a larger window, scroll to every category, drag a slider, edit border color,
and reach the final controls using Tab / Shift+Tab. Open a child settings screen
and return, then resize. Confirm the footer remains accessible, the list does
not draw over it, focus scrolls into view, and the scroll position is preserved
within the new viewport's bounds.

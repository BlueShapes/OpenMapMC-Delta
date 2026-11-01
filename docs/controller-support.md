# Controller support

OpenMapMC Delta optionally integrates with Controllable / Controllable Delta. No
controller mod is required to load Odyssey, and no extra controller driver or
mandatory dependency is added. The integration is included in all four version
roots (`1.20.1`, `1.21.1`, `26.1.2`, and `26.2`). Install a controller mod build
that matches your Minecraft version and loader.

In Controllable Delta's binding settings, assign **Open Fullscreen Map**
(`key.odysseymap.open_fullscreen`) to a controller button. You can also assign
Odyssey's other registered Minecraft key bindings if your controller mod exposes
custom mod bindings. Button remapping and Shift layers remain owned by
Controllable Delta.

Recent Controllable Delta builds recognize OpenMap's fullscreen profile and use
these map-specific defaults. They apply only to the fullscreen map; settings,
editors, inventory screens, and other mods retain their normal GUI bindings.

| Control | Fullscreen action | Popup / focused waypoint list |
| --- | --- | --- |
| Left stick | Move the cursor freely | Move the cursor freely |
| Left stick press / LT | Right-click action menu | Ordinary right-click behavior |
| Right stick | Pan the map | Pan with the list open; paused while a popup is open |
| Right stick press | Recenter on yourself | Recenter on yourself |
| D-pad right | Open and focus / close waypoint list | Close the list |
| D-pad left | Unassigned | Unassigned |
| D-pad up / LB | Zoom in | Select the previous item |
| D-pad down / RB | Zoom out | Select the next item |
| RT | Confirm with the normal left click | Activate the option under the cursor |
| START | Close the fullscreen map | Close the fullscreen map |

The controller cursor follows selected popup and list rows, excluding headers
from popup choices and excluding hidden or clipped rows. Moving through list
rows does not select or recenter a waypoint until RT confirms the row. Category
headers remain selectable with RT to collapse or expand them. Changing focus
back to the map restores zoom behavior for up/down and the bumpers.

日本語: 全画面マップでは左スティックがカーソル、右スティックが地図移動です。
L3／LTでアクション、R3で自位置へ戻り、→で一覧を開いてフォーカス／閉じます。
↑／LBは拡大、↓／RBは縮小ですが、メニューや一覧にフォーカスがある間は
上／下の項目選択になります。RTでカーソル位置を決定し、STARTでマップを閉じます。
←は未割り当てです。各割り当てはControllable Delta側で変更でき、シフト層も反映します。

Older Controllable builds without the fullscreen profile keep the previous GUI
actions: D-pad navigation, A select/drag, X context, right-stick scroll/zoom,
and Y back. The profile is optional, and requires matching builds of both mods
for the installed Minecraft version and loader.

The map hints read the configured profile buttons, including alternate bindings
and the active Shift layer. Unbound buttons are omitted rather than displaying
their base-layer key. Older integrations keep their generic GUI actions.

In **26.2**, hints default to **First use**: the first eligible controller guide
appears for eight seconds, then stays hidden across map openings, world changes
and game restarts. Opening the map with a keyboard, without a connected controller,
with F1 hiding the HUD, or without room to draw the guide does not consume it.
The setting is client-wide, not per world. Under **Map Settings → Integrations**,
choose **Always** or **Never**, or use **Show controller guide again** to replay it.
Replay also selects First use; changing the mode back to First use resets the guide.

26.2 draws buttons using Controllable's actual `buttons.png` bitmap-font assets
through its public `ClientHelper.getButtonComponent(int)` API. It follows the icon
theme selected in Controllable (Xbox, PlayStation, Switch, etc.), without copying
assets or adding a mandatory dependency. Styled clipping preserves the button
font. Other Minecraft roots retain their earlier text/continuous hint behavior.

日本語: 26.2の案内は初回に8秒だけ表示し、次回以降は非表示です。
「設定 → 連携 → コントローラー案内」で常時表示／表示しないへ変更でき、
「操作案内を再表示」で初回案内を再生できます。ボタン画像・シフト層・再割当は
Controllable側の設定に従います。

Expand the sidebar with its arrow button. Each fully visible marker row has
separate navigation points for its favorite star, visibility eye, and label.
Click a label to center the map on the marker, or double-click it to edit the
waypoint. The footer's previous/next buttons page through longer lists. While a
marker menu is open, navigation targets only its actual options; the menu header
and controls behind it are excluded.

Settings, waypoint forms, and icon selection continue to use their normal
Minecraft widgets and Controllable's standard navigation. Text entry still
requires a keyboard or another text-input method supplied by your controller
mod. Odyssey does not include an on-screen keyboard.

The fullscreen profile uses four public reflection methods with only JDK types:
`getControllerProfileId()`, `handleControllerAction(String,double,double)`,
`panControllerMap(double,double)`, and `getControllerFocusPoint()`. The ID is
`openmapdelta:fullscreen`. Pointer coordinates and pan amounts use GUI pixels;
positive pan X is east and positive pan Y is south. Unhandled clicks fall back
to Controllable's ordinary mouse emulation. Menu/list row changes return an
optional cursor position so confirmation targets the displayed row.

The older optional adapter uses Controllable's `GATHER_NAVIGATION_POINTS` event and
`BasicNavigationPoint(double, double)` through reflection. It supports both the
older typed Framework event registration and the newer `register(Object)` API.
Other screens keep their default navigation targets. An absent controller mod is
silent; an incompatible optional API is reported once without preventing the
map from loading.

Standalone checks do not start Minecraft:

```powershell
.\scripts\test-controller-navigation.ps1 -JdkHome $env:JAVA_HOME
.\scripts\test-controller-bridge.ps1 -JdkHome $env:JAVA_HOME
.\scripts\test-fullscreen-controller.ps1 -VersionRoot all -JdkHome $env:JAVA_HOME
.\scripts\test-controller-hints.ps1 -VersionRoot all -JdkHome $env:JAVA_HOME
```

Java 17 or newer is sufficient. The checks cover row geometry and scrolling,
popup clipping, both event registration signatures, optional-mod absence,
duplicate registration, semantic map/menu/list routing, safe pan and list scroll,
Shift-aware hints, and failure isolation before replacing navigation targets.
A real controller session is still needed to verify device behavior and
the installed controller mod's mouse emulation.

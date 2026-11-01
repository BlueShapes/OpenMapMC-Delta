# Fullscreen train tracks

The grid button in the fullscreen map's top-right corner opens **Map layers**.
Toggle **Chunk grid**, **MTR tracks**, and **Create tracks** independently.
These choices are saved in `config/openmapdelta/config.json`; the track options
are `fullscreenShowMtrRails` and `fullscreenShowCreateRails`, both enabled by
default. Station waypoint settings do not affect track visibility.

Create tracks are orange and MTR tracks are cyan, with dark outlines. Paths
follow the actual curves and remain beneath waypoint and player markers.
Keyboard arrows and Enter/Space, mouse input, and the existing controller
navigation work in the layer menu. Escape or clicking outside closes it.

Tracks come from each mod's synchronized client data in the current dimension.
Create graph changes update while the map is open; MTR refreshes about once per
second. Closing and reopening the map refreshes the data. Disconnecting or
changing client levels clears the session snapshot. No chunks are loaded and
no track data is written to disk.

Create's synchronized graph can include distant tracks. MTR 3 synchronizes
tracks near the player, so its overlay shows the currently available area.
MTR 4 uses its current client rail collection. Only MTR's TRAIN transport mode
is included. Geometry extraction is bounded to 16,384 tracks and 262,144 points
per source; curves use at most 128 segments. Rendering clips to the viewport
and draws at most 8,192 visible segments per frame.

フルスクリーンマップ右上のグリッドボタンから「マップ表示」を開き、
「チャンクグリッド」「MTRの線路」「Createの線路」を個別に切り替えられます。
設定は保存され、線路は曲線を含めて表示されます。MTR 3の表示範囲は、
現在クライアントに同期されているプレイヤー周辺の線路に限られます。

Run the focused headless checks with Java 17 or newer:

```powershell
.\scripts\test-transit-rails.ps1 -VersionRoot 1.21.1 -JdkHome $env:JAVA_HOME
.\scripts\test-fullscreen-rails.ps1 -JdkHome $env:JAVA_HOME
```

Pass `-MtrJar <path-to-MTR-4-jar>` to the reader test script to also check real
straight and curved geometry from that JAR without starting Minecraft.

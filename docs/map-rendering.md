# 地形の描画 / Terrain rendering

## 縮小表示

全画面地図とミニマップは、GUI の倍率を考慮して、画面の物理ピクセル数に
合わせた描画画像を作ります。GUI 倍率が 3 の場合に、地形を GUI の 1 ピクセル
まで先に粗くしてから画面上の 3×3 ピクセルへ引き伸ばす処理を避けます。
設定の追加操作は不要です。表示範囲、ズーム倍率、マーカー、チャンク境界の
ワールド座標は変わりません。

保存された地形は従来どおり 1 ブロック単位です。描画画像を変えてもキャッシュを
削除したり、探索済みの情報を書き換えたりしません。原データより細かい画像を
作って情報を増やすことはなく、複数ブロックが画面の 1 ピクセルに収まる場合は、
そのすべてを個別に見せることはできません。全画面地図は、その画面ピクセルに
重なる既知の地形色を面積に応じて平均します。

メモリ使用量には上限があります。全画面の画像は、スクロール用の余白を含めて
最大 4096×4096 ピクセル、ミニマップは最大 2048×2048 ピクセルです。
この上限を超える極端に大きな表示では、画像サイズを上限内へ収めます。
未探索・未取得の地形情報を新しく得る機能ではありません。

変化のない画像は再利用します。全画面では地形が変わった範囲だけを CPU 側の
画像にコピーし、ミニマップでも移動・地形更新・表示設定などに変化がなければ
再合成とアップロードを省きます。洞窟の Y 帯を切り替えるときの一時的な描画保持と
期限は維持します。

## 高低差の陰影

ミニマップと全画面地図は、隣接ブロックの高さが変わる縁に陰影を付けます。
低い側を暗く、高い側の縁を少し明るくし、北西から光が当たる向きを基準にします。
高さの差が大きいほど陰影を強めますが、元の地形色を判別できる範囲に抑えます。
平らな部分は元の色を保ちます。設定の追加操作は不要です。

保存済みの高さを使って描画時に計算するため、地形の再探索やキャッシュの削除は
不要です。古いデータなどで高さが不明な場所には陰影を付けず、高さの取得後に
反映します。透明な場所や未探索部分を段差とみなすことはありません。
タイルの境目でも、隣の既知地形を使って陰影をつなげます。

地表と洞窟の選択中の地形に適用し、サーバーの洞窟表示ポリシーを維持します。
縮小時は陰影を付けた地形色を平均するため、細かい境界は縮尺に応じて混ざります。
保存された色・高さ・草色は書き換えません。Web 地図の色はこの変更の対象外です。

## English: zoom-out rendering

Both maps account for the window's GUI scale when choosing terrain raster density.
They target physical display pixels instead of first reducing terrain to one texel
per GUI pixel. World coverage, zoom, markers, and chunk-grid coordinates stay the
same; no new setting is required.

Stored terrain retains its existing one-block resolution. The change does not
rewrite or delete explored terrain. Multiple blocks that fit into one physical
pixel still cannot be individually distinguished. Fullscreen rendering averages
known terrain by its exact overlap with each output pixel, including fractional
block boundaries.

Fullscreen textures remain bounded to 4096 pixels per side, including overscan;
minimap textures are bounded to 2048. Displays beyond those limits use bounded
rasters. The renderer does not invent detail finer than the source terrain or
obtain unexplored data. Unchanged images are reused, changed fullscreen regions
are copied incrementally, and minimap cave-layer retention remains time-limited.

## English: terrain relief

Both client maps shade boundaries between blocks with different known heights.
Lower edges receive a shadow and upper edges a restrained highlight, using a
fixed northwest light direction. Larger height differences strengthen the effect
within a bounded range. Flat areas retain their original color; no setting is
required.

Relief uses cached heights while rendering, so existing height-bearing terrain
does not need to be explored again. Unknown heights, transparent pixels, and
unexplored neighbors do not create cliffs. Old data without heights gains relief
after heights are sampled. Existing neighbors are considered across tile edges.

The active surface or cave layer receives relief under the existing server policy.
Fullscreen rendering applies shading before area averaging, so small boundaries
blend with surrounding terrain at distant zoom levels. Stored colors, heights,
and biome-grass colors are unchanged. Web map colors are outside this change.

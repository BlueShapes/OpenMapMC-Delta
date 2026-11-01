# Feature, compatibility and porting register

This register describes the current public source snapshot. Stable F/P IDs are retained to help port features between Minecraft versions; private work logs, chat references and previous fork commit tables are not part of the public repository.

Upstream comparison baseline: Odyssey Map 1.3.0 by NAIZO, upstream commit `d4bf73ec0b69afa78a84d897a8d50f7d87480b4c`. Preserve its Apache-2.0 attribution and the third-party asset notices. The new repository starts with its own root commit; the upstream reference is attribution, not an imported Git parent.

“Implemented” describes source availability. It does not imply every loader, GPU, external mod combination or physical input device has been tested. The version-specific source, metadata and tests take precedence over older prose.

## Version scope

| ソースルート | ローダー | Java | 現行ビルド設定の注意 |
| --- | --- | --- | --- |
| `1.20.1` | Fabric / Forge | 17 | Forge ビルド依存と導入先の Forge パッチ番号は区別する |
| `1.21.1` | Fabric / NeoForge | 21 | **Forge 版は存在しない**。過去の「Forge 1.21.1」依頼は NeoForge 環境へ修正された |
| `26.1.2` | Fabric / NeoForge | 25 | GUI の抽出描画 API を使用する版。旧版の render 実装をそのままコピーしない |
| `26.2` | Fabric / NeoForge | 25 | GUI／ワールド描画 API、Mixin ターゲットを版に合わせる。NeoForge 26.2.0.88 は binary patch pipeline を使用 |

| 要件群 | 1.20.1 | 1.21.1 | 26.1.2 | 26.2 |
| --- | --- | --- | --- | --- |
| 以下 F01〜F32、F38〜F41 の共通機能 | 実装あり | 実装あり | 実装あり | 実装あり |
| F17: Emojiful Deltaの名前内alias省略／outline／alpha／太字幅の互換 | raw省略呼出し確認・連携未検証 | raw省略呼出し確認・連携未検証 | raw省略呼出し確認・連携未検証 | 実装・headless／native API／両MOD両loader buildP10（実GPU／物理入力未検証） |
| F03: toastをミニマップより手前に描画 | 既存動作・両loaderのnative順／深度検査済み（修正不要・実GPU未検証） | 既存動作・両loaderのnative順／深度・target layer helper検査済み（修正不要・実GPU未検証） | 既存動作・両loaderのnative順／stratum検査済み（修正不要・実GPU未検証） | 実装・handler／native anchor／両loader build（実GPU未検証） |
| F04: 四隅の上下余白・背景行高・物理ピクセル整列の補正 | 未反映 | 実装・headless／build 検証済み | 未反映 | 未反映 |
| F04: 四隅の横揃え方式の選択（マップ端／列中央） | 未反映 | 実装・headless／build 検証済み | 未反映 | 未反映 |
| F04: MAP_EDGE の実マップ左右端基準と対称余白 | 未反映 | 実装・headless／NeoForge build | 未反映 | 未反映 |
| F22: 選択したプレイヤーのワールド内アイコン／ビーコン | 実装あり | 実装あり | **未反映** | 実装あり |
| F33: NeoForge 1.21.1 固有の修正 | 対象外 | 実装あり | 版固有 API の再確認 | 版固有 API の再確認 |
| F34: Iris 使用時のワールドラベル背景修正 | 同修正の対象外 | 実装あり | 同修正の対象外 | 同修正の対象外 |
| F35〜F37: Smart Deathpoints の統合 | Fabric / Forge に統合 | Fabric / NeoForge に統合 | **未統合** | Fabric / NeoForge に統合 |
| F42: 権限付きサーバー設定画面・即時適用 | 実装あり | 実装あり | **未反映** | 実装あり |
| F43: 共有 waypoint 作成・編集・削除成功の構造化 INFO イベント | 実装・headless／Forge build 検証済み | 実装・headless／NeoForge build 検証済み | 実装・headless／NeoForge build 検証済み | 実装・headless／NeoForge build 検証済み |
| F44: 天井のある次元の実地形 surface | 実装・headless 検証済み | 実装・headless 検証済み | 実装・headless 検証済み | 実装・headless 検証済み |
| F45: ミニマップの地表／自動 Y／手動洞窟レイヤー | 実装・headless 検証済み | 実装・headless 検証済み | 実装・headless 検証済み | 実装・headless 検証済み |
| F45: Y 帯切替時の一時的な描画保持・点滅防止 | 実装・pixel／両ローダー build 検証済み | 実装・pixel／両ローダー build 検証済み | 実装・pixel／両ローダー build 検証済み | 実装・pixel／両ローダー build 検証済み |
| F11／F45: 周囲の地形欠落修正・露天時の自動 Surface | 実装・headless／両ローダー build 検証済み | 実装・headless／両ローダー build 検証済み | 実装・headless／両ローダー build 検証済み | 実装・headless／両ローダー build 検証済み |
| F46: 外部マップの遠隔 player 連携 | 実装・headless／両 loader build・Forge 配置済み | 実装・headless／両 loader build・NeoForge 配置済み | 実装・headless／両 loader build 済み | 実装・headless／両 loader build・NeoForge 配置済み |
| F47: ミニマップ内外の他 player ラベル選択 | 実装・headless／両 loader build・Forge 配置済み | 実装・headless／両 loader build・NeoForge 配置済み | 実装・headless／両 loader build 済み | 実装・headless／両 loader build・NeoForge 配置済み |
| F48: 近傍エンティティの小型 MOB アイコン・高低差フェード | 実装・headless／両 loader build・Forge 配置済み | 実装・headless／両 loader build・NeoForge 配置済み | 実装・headless／両 loader build 済み | 実装・headless／両 loader build・NeoForge 配置済み |
| F48: MOB 表示の初期値 ON | 未反映・初期 OFF | 未反映・初期 OFF | 未反映・初期 OFF | 実装・実 config／両 loader JAR 検証・NeoForge 設定／配置済み（実機表示未検証） |
| F48: 魚・MODのhead優先モデル画像・cache・縦横比保持 | 未反映 | 未反映 | 未反映 | 実装・headless／両loader build（実GPU／実MOD外見未検証） |
| F49: 全画面／ミニマップの物理画面 pixel 密度 | 実装・pixel／両 loader build・Forge 配置済み | 実装・pixel／両 loader build・NeoForge 配置済み | 実装・pixel／両 loader build 済み | 実装・pixel／両 loader build・NeoForge 配置済み |
| F50: 高さが変わる境界の地形陰影 | 実装・pixel／両 loader build・Forge 配置済み | 実装・pixel／両 loader build・NeoForge 配置済み | 実装・pixel／両 loader build 済み | 実装・pixel／両 loader build・NeoForge 配置済み |
| F30: Identifier から生成されるキー割当カテゴリ見出しの翻訳 | 旧 API の既存 key を維持 | 旧 API の既存 key を維持 | 未反映・P06 | 実装・16 catalog／両 loader JAR 検証・NeoForge 配置済み（実機表示未検証） |
| F08: 全画面マップを toast より前に描画 | 未反映 | 未反映 | 未反映 | 実装・handler／実 target bytecode／両 loader build 検証・NeoForge 配置済み（実機表示未検証） |
| F51: 全画面の自プレイヤー頭の近接縮小・4xズーム | 未反映 | 未反映 | 未反映 | 実装・geometry／実 RenderUtil capture／両 loader build 検証・NeoForge 配置済み（実機表示未検証） |
| F52: 全画面controller action／list focus／専用hint API | 実装・policy／両loader build | 実装・policy／両loader build | 実装・policy／両loader build済み（配置先未指定） | 実装・実widget／policy／hint／両loader build（実機入力未検証） |
| F52: 初回8秒の案内・Controllableボタン画像 | 未反映 | 未反映 | 未反映 | 実装・policy／styled glyph／両loader build（実GPU／device未検証） |
| F53: 任意の地表地名・分岐する川・国／地域の階層編集 | 未実装 | 未実装 | 未実装 | 実装・対象core／service／editor helper・両loader build／JAR検証（実機入力／GPU未検証） |
| F54: 標準サイズの設定ボタン・カテゴリーヘッダー・スクロール一覧 | 実装・geometry／両loader build（実機入力／GPU未検証） | 実装・native API review／両loader build（実機入力／GPU未検証） | 実装・native API review／両loader build済み（配置先未指定・実機未検証） | 実装・native API review／両loader build（実機入力／GPU未検証） |

## 3. 差分と保持すべき要件

### F01 — Fork の名称・帰属・配布識別子

- 配布／リポジトリ名は **OpenMapMC Delta**、ゲーム内／Web 表示名は **OpenMap Delta**。MOD ID とアセット namespace は `openmapdelta`、Java package/group は `net.aosankaku.openmapdelta`。
- 配布 JAR basename と版別 Gradle project 名は `openmapmcdelta`。現行 VERSION は `1.3.0`。改名だけで互換用の識別子まで一括置換しない。
- 上流の帰属・Apache-2.0 の LICENSE／NOTICE と AoSankaku の変更表示を維持。MTR アイコンの MIT 表示も配布 JAR に含める。

### F02 — 設定・キャッシュ移行と旧識別子の互換維持

- 初回に `config/odysseymap/**` の設定等を `config/openmapdelta/**` へ取り込み、タイルは `config/odysseymap/cache/**` から **ゲームディレクトリ直下の `openmapdelta/**`** へ移す。新ルートの下に余計な `cache` 段を作らない。
- 新配置の既存ファイルを優先し、不足分だけ取り込む。旧設定ツリーはバックアップとして保持。移行完了印で、後にユーザーが削除したファイルを再復元しない。不安全なパス／I/O エラーでは既定値の読み書き前に停止し、再試行可能にする。
- **変更しない互換識別子**: `data/odysseymap-world-id`、`data/odysseymap-shared-waypoints.json`、駅アイコン `symbol:odysseymap:{mtr_station,create_station}`、駅 ID seed `odysseymap:transit:`、cookie `odyssey_session`、環境変数 `ODYSSEY_WEB_PASSWORD`、ブラウザー設定 `odysseymap.chunkGridVisible`、選択済み provider key `odysseymap`。
- 1.20.1／1.21.1／26.2 Smart Deathpoints の server recovery store `data/odysseymap-death-recovery.json` も旧識別子のまま維持。client deletion state は `config/openmapdelta/deathpoint-deletions.json`。通常 waypoint ファイルだけを移せばよいとは扱わない。
- 旧 namespace の任意サーバーチャンネル、`[Odyssey Map:<uuid>]` と ACK（旧 54 文字形式を含む）、`odysseymap.shared.*`、既存翻訳／キーバインド ID も維持。runtime の MOD 検出は `openmapdelta` を使う。

### F03 — ミニマップの相対サイズと小画面での可読性

- 固定 px サイズから GUI 短辺の 1/3 を基準にした倍率へ変更。**現在の `minimapScale` 初期値は `0.9`、許容範囲 `0.25`〜`2.0`**。古い `minimapSize` は表示サイズを支配しない。
- GUI scale、小ウィンドウ、端への配置、枠／情報欄込みの画面内収まりを扱う。1 GUI pixel 未満の地図は非表示。不正値だけを既定値へ戻し、他の設定読込を失わせない。
- 文字の最小物理 line height **16px** を保証し、丸地図の中心・マスク・境界を一致させる。情報枠／文字の拡大で terrain map 自体や scan 範囲を広げない。古い説明の `1.0`／以前のサイズ初期値を新規 Fork の初期値として復活させない。
- 初期位置は右上、north-up。heading-up は任意設定。地図を自動回転する以前の初期動作へ戻さない。
- 通知（進捗等のtoast）はミニマップ全体より手前にする。26.2の両loaderはMixinGuiをToastManager.extractRenderState直前に置き、通知の新stratumがその上に来る。2026-10-30の他版検査で1.20.1／1.21.1はHUD内のmap→depth clear→toast（Z800）、26.1.2はHUD内のmap→toastの新stratumを実bytecode／sourceで確認し、同じlate hook不具合がなくproduction修正不要と判断。1.21.1のnative gameはNeoForge21.1.80、指定21.1.262はlayer helperを別途確認した範囲で、同版patched game全体の検証とはしない。通常screenとの順序・表示条件・位置は維持し、26.2全画面地図はF08の別要件を維持。回帰・版別証拠／[26.2 runner](../scripts/test-map-toast-layer.ps1)。

### F04 — 六つの情報欄と計測値

- 上下と四隅の 6 slot を設定・検索可能にする。幅広い座標／名前／ID／最寄り情報は上下に限定し、四隅は短い値。空欄を選択可能。
- 初期配置は上に biome、下に XYZ、右下に 24h 時計、左下に経過日数、上の両隅は空。白文字・暗い背景で長い情報も画面内に配置し、座標表示スイッチを適用。
- daytime と 06:00 offset、24,000 tick 周期、カスタム秒/tick（初期 `0.05`）、速度の実時間平均（初期 5 秒、テレポート／次元変更で再採取）、FPS 平均、session time、JVM heap、36 inventory slot、睡眠可否などの意味を保持。
- TPS はローカルサーバー実測／リモート vanilla time packet の推定を区別し、古い／不足した値は `—`。ping→tick は対象版の tick rate に従う。遠隔の最寄りプレイヤーは任意サーバー対応または F46 の公開外部位置から取得できる。
- 1.21.1 は 2026-10-15 に四隅の背景行高と上下の frame inset を共通化し、文字を行内で上下中央へ配置。左側／右側の端を固定し、背景矩形・文字原点を同じ physical pixel grid に揃える。最小 16 physical px の文字サイズと省略を維持し、padding 込みの行が収まらない極小 viewport では行全体を省く。他版へのこの配置補正は未反映。[renderer capture regression](../scripts/test-minimap-info-renderer.ps1) を参照。
- 同版の設定 → ミニマップ → 四隅の横揃えで、MAP_EDGE（初期値、実際のマップ左右端から等しい内側余白）と CENTER（情報 frame の左右 1/4／3/4 をそれぞれ列中心とする）を選択。MAP_EDGE は最低幅 120 の情報 frame に左右位置を引きずられず、各ラベルをマップの半幅に収めて省略し、省略記号も入らなければ非表示。上下の対は同じ端または列中心を共有し、physical pixel 丸めの誤差は 1px 以内。中央 TOP／BOTTOM と縦位置は影響を受けない。設定は即時保存・次の描画へ反映する。他版はこの選択設定も未反映。
- 選択肢の漏れ確認: weather、FPS、12/24h/custom clock、ticks/seconds、sleep availability、player count、X/Y/Z 個別、elapsed days、target/feet light、speed、session/real clock、chunk coordinate/local coordinate、TPS、latency ticks、RAM%、free slot、XYZ、dimension ID/name、biome ID/name、target block ID/name、nearest player name/XYZ、nearest waypoint name/XYZ。

### F05 — 自分／他プレイヤー／全画面のアイコン

- ミニマップの自分と他プレイヤーを**別設定**にする。両方と全画面の現在の初期値は **PLAYER_HEAD**。顔＋hat layer、triangle、vanilla pointer を選択可能。ミニマップの player scale 初期値 `0.6` はサイズ倍率であり、F03 の地図倍率と混同しない。
- 旧 `minimapPlayerIcon` しかない設定は、その値を自分と他プレイヤーの両方へ引き継ぐ。旧 `showPlayerHead` は新 `fullscreenPlayerIcon` がないときだけ取り込む。
- 顔は通常正立、回転を有効にした場合は補間済み向きへ追従。heading-up では地図の回転を補正。heading を持たない遠隔 snapshot は正立のまま。safe render mode でも顔／pointer を表示。
- 26.2 の全画面の自分の頭は F51 の通常サイズ縮小／cursor接近時の縮小を適用。他playerのsizeとミニマップ／Webは従来のsizeを維持する。
- pointer は現在**白い map-player sprite を UUID 色で tint**。UUID.hashCode の下位 24 bit→HSV、明度 `0.9`。server/team override の再現ではない。古い green item-frame sprite の仕様を復活させない。回転軸を alpha のある領域の中央に置き、triangle の最終形状（高さ:幅 = 3:2）も維持。parent の非一様 scale、pose/shader の復元、1.20.1 atlas と新しい独立 sprite の差を確認する。

### F06 — ミニマップのウェイポイント名・遠距離表示

- 最寄りの通常ウェイポイント名は初期 5 個、水平距離で選択。`minimapLabelLimit=-1` は全件、`0` は通常地点の名前なし。目的地／通知は強調し、通常地点の枠を消費しない。
- 名前距離の初期値 150m 内では個別の名前＋距離、遠距離では簡潔な距離／重なった group の名前を使い分ける。遠方の edge icon を外側へ配置し、距離だけの文字と marker は waypoint 色 50%＋白 50% で明るく保つ。
- ラベルは地点近くを保ち、顔や別 icon を避けるために遠くへ移動させない。半透明黒背景と文字前面描画を保持。waypoint icon/name scale 初期値 `0.65`。

### F07 — HUD の表示条件・枠・色

- F1 とメニュー非表示の状態で情報欄だけが残らない。chat を開いた場合は地図・情報欄を表示し、inventory/container 等では設定に従って一緒に隠す。
- solid/dotted の枠を選択、初期 solid gray `#FF808080`。`#RRGGBB` は不透明、`#AARRGGBB` は alpha を保持。枠の厚さは独立設定。

### F08 — 全画面 UI・sidebar・F1・再センタリング

- sidebar は初期閉じた状態で開閉可能。閉じる操作、地図の最大表示、不要な上部 Waypoint ボタンの削除を保持。
- Recenter は地図中心が自分から外れたときだけ右下へ表示。sidebar 開閉／リサイズにも追従し、クリック後即座に隠す。
- zoom button は初期非表示、設定で復活可能。wheel zoom は維持。地図上端の layer controls と座標 footer を持つ。
- 全画面地図の F1 で header／button／sidebar／footer を隠し、画面全体を地図にする。ドラッグ・zoom・pan 状態は維持し、地図から戻っても F1 状態を維持。
- 26.2 は全画面マップを toast より前にする。loader 別 `MapScreenToastLayerMixin` が実際の画面抽出操作の直前に toast を一度だけ抽出し、per-invocation の共有 flag で後段の重複抽出を防ぐ。通常画面／画面なし／reload overlay は vanilla の順序を維持。toast の queue／update／寿命／HUD 非表示判定は変更せず、マップ背景の透明度も維持する。NeoForge の `ClientHooks.extractScreen` による pre/post event と背景 screen stack を通す。他 3 版には未反映。

### F09 — 地形表示・座標と描画負荷

- pan／zoom／sidebar 幅／画面サイズが変わっても、キャッシュ済み地形を同じ world 座標へ配置する。terrain composition は revision／状態変化に応じて更新し、待ち状態と safe mode を維持。
- 低倍率の平均色・細かい terrain raster、surface height と biome color を用いた地形・草色、線／marker が読みやすい重なり順を保持。player face の zoom 連動と最大サイズを適用。
- 全画面は26.2のみ最大 **4x**（F51、1x→2x→4xの詳細step）、他3版は最大2x。二つ増やした zoom-out step とdefault zoomの設定範囲1〜18は維持。overscan／snapped sampling origin と bounded stored-tile prefetch で滑らかな drag。空の未探索 tile を大量生成しない。dimension 表示、実倍率表示、off-map edge marker の本来の world 座標への hit test を維持。
- 1.21.1 の item icon capture は輪郭／色を保ち、Web capture が framebuffer、viewport、shader、scissor、mask、blend 等の状態を成功／失敗両方で復元する。

### F10 — チャンク境界グリッド

- X/Z の 16 倍数が境界。負座標を含め、pan／zoom 時も terrain と一致。0.15x まで各境界を表示、さらに低倍率では 2,4,8…chunk ごとへ集約。
- `fullscreenGridThickness` 初期 `0.5` GUI pixel、範囲 `0.25`〜`4.0`。密な倍率では細くし、隙間が残る上限と GUI scale を適用。表示切替を保存。

### F11 — 洞窟の離散 Y 帯・サーバーポリシー

- surface と洞窟を切替。帯の幅 `caveLayerStep` は初期 8、範囲 1〜64、次元の最低 build height に揃える。上下限は inclusive、端の短い帯・負 Y・設定変更時の snap を扱う。
- 帯の下端付近の床を loaded client chunk だけで採取。surface と別 cache。ミニマップは既定 surface で、F45 の自動／手動洞窟帯も独立選択できる。カーソル surface Y は既知の最高値、不明は `?`。
- 選択帯より列の地表が低い場合は実 surface を採る。空洞の床を 64 block で打ち切らず次元の最低 Y まで探す。実床なしは unknown を維持し、cave の null／NONE MapColor は既存 stone fallback に正規化する。live と detached worker の両経路で適用する。
- 任意サーバー `server.json` の `allowCaveMap=false` で全画面を surface へ戻し、Web の cave data も抑制。無効な policy は fail closed。隠れた chunk の要求・生成をしない。

### F12 — 永続 terrain cache・非同期処理・テレポート後の生成

- 圧縮 `.tile.z` の永続 cache、版 1=色／版 2=height／版 3=biome color の読込互換を保持。embedded world/dimension/座標、magic、長さ・展開サイズ・末尾の検証、不正データの無視、atomic replacement を維持。
- unknown／未観測 pixel で既知地形を消さない。旧 cache は再 scan により height／色情報を補う。版更新で勝手に全 cache を削除しない。
- disk I/O は非同期、generation/session を越えた古い結果を拒否。write を直列化し終了時 flush。bounded prefetch と revision による地図更新、地形 scan の負荷設定を保持。
- テレポート時は新しい現在地周辺を優先、古い遠方 queue が近傍生成を妨げない。移動／次元切替／logout 時の queue/cache isolation を維持。

### F13 — サーバー／ワールド識別と取り違え防止

- cache scope は接続先／local save、seed 由来 vanilla 64-bit 値、任意サーバー UUID、次元、layer。host の大文字小文字・既定 port 25565 を canonicalize、非既定 port は分離。DNS を IP 解決して異なる host を勝手に統合しない。
- `data/odysseymap-world-id` の UUID を再起動で維持。壊れた identity を上書きしない。UUID 対応接続は確定まで memory-only、失敗時も別 world の cache を推測して使わない。
- optional payload／Fabric login query／Forge・NeoForge support negotiation、元接続を捕捉した callback、logout clear、遅延 UUID による stale disk load 拒否を保持。
- identity を持たない旧 address-only cache は残して自動 import しない。サーバー未導入で同一 seed 由来値の別 world を確実に区別できない制約を記録する。

### F14 — JourneyMap／Xaero ウェイポイント import

- JourneyMap 5 JSON、JourneyMap 6 の uncompressed NBT `WaypointData.dat`（古い JSON より native を優先）、現行／legacy Xaero ディレクトリを扱う。元ファイルは変更しない。
- source dimension、unknown Y、Nether ×8 と負座標 floorDiv、個別／group visibility、color、name+dimension+XYZ の重複排除、invalid／skip 件数を保持。symlink を読まず bounded NBT decoding。import 地点を current world/server に限定し、既存手動地点の旧 scope は不用意に変更しない。
- scan は非同期、接続変更で旧 scan を無効化。dataset fingerprint による一度の案内、clickable `/odysseymap import`、atomic save と失敗時 rollback を保持。

### F15 — ウェイポイント編集・戻る操作・metadata

- name／XYZ（正確な Y を含む）／color／icon／category／visible／favorite を保存。選択 icon の preview、hidden point の編集、削除確認を維持。
- 編集画面・icon picker・設定から Esc／Back で元の地図へ戻り pan／zoom／編集中 draft を保持。変更済み Cancel/Esc は discard confirmation、未変更は直接終了。長い name/coordinate の draft と resize を扱う。背景 blur より後に panel／text／widget を一度だけ描く。
- 一時地点／駅／共有地点／BED／deathpoint の編集可否・専用 persistence を通常地点と混同しない。

### F16 — icon picker・駅 symbol・hover

- flat item と mob face、後に追加した登録 MOD item／minecart の GUI model、固定駅 symbol を選択・保存。未登録／削除／未ロード icon は色 marker に fallback し、保存 ID は消さない。
- 検索は名前、`@mod`、`&id`、`#tag`、`$tooltip`、quote、空白 AND、`|` OR、`-term` 除外と highlight。JEI 導入を必須にしない。
- icon・label の hover と hit test が同じ地点を指す。非表示／culling 済み地点が click を横取りしない。CJK の細い字を潰さない outline、明暗に応じた halo と黒白 icon frame を維持。
- MTR logo は上流からそのまま継承し MIT attribution を同梱、Create symbol は生成した簡潔な pixel art、nearest-neighbor 縮小。

### F17 — Emojiful 任意連携

- Emojiful の標準／custom／world recipe emoji を追加 tab に表示。名前／alias／alt／`@emojiful` を検索。列挙するだけで画像 download を始めず、可視 icon だけ既存 loader で読む。
- 保存 ID は `emoji:emojiful:<first nonempty alias の SHA-256>`。ゲームは現在の animation frame、Web は更新時の bounded capture。独自に画像を取得しない。
- 選択時の画像をwaypointへ保存しない。同じ識別aliasの差替画像をEmojifulが読み込めば現在の画像へ追従し、同aliasが別絵文字へ再利用されてもその絵文字を使う。先頭の識別alias変更は別IDとなる。外部URLの変更／削除だけをmapが監視・強制再取得することはなく、Emojifulが既存frameを保持している間は旧画像が見える場合がある。
- MOD／emoji 削除、load 中／失敗は fallback。選択を残し復活時再解決。reflection・resource reload・logout の invalidation は Emojiful 所有 texture を破棄しない。
- Emojiful Deltaの名前等に埋め込むaliasの表示は、選択済みiconの直接texture描画と区別する。26.2は2026-11-02にraw省略のsource offset・10px advance・caller alpha・literal入力表示を修正。outline passでは絵文字の幅を保ち画像を省き、foreground画像を一度だけ描く。通常文字のoutlineと入力外の原子的な記号化を維持する。EmojifulのoutlineSequence APIを持つ対応buildとの組合せで検証し、元alias／永続IDは変更しない。他版未移植P10。調査／修正証拠。

### F18 — リスト・category・全画面の文字量

- waypoint list の選択／double click 編集、favorite／visibility、search、paging、hover hint を保持。category を作成・編集・折畳みでき、折畳み状態を適切に扱う。
- 全画面 waypoint label の zoom threshold（初期 `0.5x`、範囲 0〜2x、0 は常時）と独立文字倍率（現在初期 `0.75`、範囲 `0.25`〜`2.0`）を提供。名前を短く整理し、距離等の過剰な文字量を削減。非表示 label は hit area を残さず icon は選択可能、player label は別扱い。
- current dimension の hidden／temporary／shared／station も list に残す。category eye は検索で隠れた member も含む全 category、Show/Hide all は filter／collapse に関係なく次元全体。普通の未分類と station default category を区別し、新規 point は visible。
- station の name／XYZ は upstream 所有、削除不可。category/color/icon/visible/favorite override は world ごとに永続化。identity 未確定時の編集は確定後に touched field だけを merge する。
- 共有地点の本人だけの visibility／favorite、creator face、BED／deathpoint 等の metadata を list 操作で失わない。

### F19 — ワールド icon・label・beam の可読性

- label と crosshair 判定の整合、terrain 越しの可視性、明るさ、CJK outline、上／下を向いた beam の全垂直 span の判定を保持。
- 以前の 256 block cutoff より遠くても world projection で表示。render limit 内では gaze／目的地／通知を優先。unknown Y は loaded chunk だけから解決し、不明時の fallback と実座標からの距離計算を保持。
- world の F1/hideGui では world icon／label／beam も隠す。地図 HUD だけの非表示と混同しない。
- **label/icon/backdrop の fade と beam fade を別設定**にする。label 開始初期 2.5 block／半分以下で非表示、beam hide 初期 5 block／倍の距離から fade。3D の player 位置で計算、camera 位置に依存しない。
- 範囲 0〜128 の有限小数、0 は各 fade 無効。label 非表示でも可視 beam を維持。temporary／notification beam は 2 秒周期で 25〜100% pulse、point／text は pulse させない。

### F20 — ワールド／ミニマップの重複 group

- 遠方で重なる waypoint を count badge にまとめ、中心 icon、white beam、名前の積重ねを表示。近い地点は個別、名前表示距離 0 なら個別を維持。label collision は上下移動で解消し icon を動かさない。
- cluster focus は dot 中心への視線と一致。focus した group の可読な名前を表示し、上限超過は初期 5 行＋残件数行。fade 済みの名前を count に含めない。64 rendered-group 上限を越えた重複 member も group count に含める。
- 下から上の順序は最寄り／遠い順／名前順を設定。目的地／通知に表示優先度を与えても row の設定順序は維持。gaze を外した場合は普通の名前だけを隠す。

### F21 — 右 click menu・一時地点・到着

- 空地点／waypoint／player に target 別の menu。header に X Y Z、header は action にしない。小画面でも row・padding を読みやすく保ち、Esc／外側 click で閉じる。
- command tree に `tp`／`teleport` がある場合だけ teleport を提示し、実行権限はサーバーが再判定。teleport と位置共有の height 制限を区別。
- 一時 waypoint は session-only、3D の半径 5 block の到着判定（X/Z は block center）。範囲内作成は一旦外へ出てから戻ったとき除去、座標編集はその判定を再初期化。

### F22 — 複数目的地・プレイヤー追跡

- 任意地点／保存地点／temporary／player への navigation を session 内で保持。初期最大 3、範囲 1〜16、arrival 初期 5m、0 は自動到着解除なし。個別 stop／全 stop は別次元の slot も解放。範囲内開始は exit/reentry、自分への navigation 禁止、stale selection を無関係な click 位置へ代替しない。arrival 前に名前 snapshot を確保し自分に到着 chat。
- waypoint と temporary は選択時点の XYZ、player は最新位置を追跡。通知の Follow selected player は初期 on、off は snapshot。情報が途切れたら最後の既知位置を保持。
- 空地点は強調 world icon／beam。**1.20.1／1.21.1／26.2 では player destination にも world icon／beam**、普通の marker visibility を切っても明示目的地は表示。未選択 player には beam を付けない。26.1.2 へのこの補正は未反映。

### F23 — 位置通知・送信者 marker・寿命・delivery

- 送信者を必ず暗黙の対象に含め、recipient list／件数／検索からは除外。他人未選択なら local-only。search、顔、multi-select、全オンライン選択（検索外／untracked／別次元も含む）、未知距離表示を保持。
- duration は現在 **5〜600 秒の log slider（1 秒 step）、初期 15 秒**。旧 cycle の 5/15/30 秒・1/2/5/10 分は途中仕様。送信者 marker は同じ sender name/icon で 1 個、resize／back で選択を保持。通知を個人 waypoint として保存可能。
- expiry は recipient の receipt／sender の queue 時刻を基準にする。地図を開いている session では expired notice の選択を保ち、編集等から戻っても消さない。remote notice は bounded、logout で queue と session marker を clear。
- 現在の送信は whisper→ACK を待ち、未確認 peer は必要に応じ**public `/me @name` へ fallback**。対応確認済み peer の失敗を public 化しない。宛先 UUID の再解決、signed message identity、notice UUID の重複防止、40 server tick＋1.1 秒の cadence、bounded pending queue を保持。
- 現行 `share` は sender marker を先に作り、remote を個別 queue する。古い説明の「overflow は batch 全体拒否／sender marker なし」「private message のみ」を仕様として復活させない。

### F24 — 通知メッセージ・context query・private message

- 全 target の通知で recipient 選択後に template／自由文を編集。meet／come／base／cave／building／trade、looked-at block、target 地点の biome／各 structure を提示。非同期 query で draft を上書きせず、Back で duration／follow／recipient を保持。
- 任意サーバーの context query は loaded chunk のみ、bounded、不完全な結果を表示、force-load／world generation をしない。未対応でも fixed/block、既知 biome、自由文を使用可能。
- payload は 220 文字上限、canonical UUID／dimension／整数座標／duration 5〜600 秒、単行・制御／formatting code 禁止。超過は Send 無効で勝手に切り詰めない。旧 default payload、`D:`／`M:` の拡張を互換読込。
- 黄色 chat line を click すると X Y Z を clipboard にコピー。player の Send message は native `/msg`／`/w` とその permission を使用し offline／利用不能時に翻訳 feedback。

### F25 — optional server・全 player snapshot

- client-only／server-only／両方を許可。server 導入を他 player に必須化しない。両側対応時は 1 秒ごとの XYZ／dimension snapshot、loaded player の tick 更新を優先。native 未対応時は client tracked と F46 の公開外部位置を使用する。
- 1.20.1 Forge と Fabric は envelope が異なるため server-wide sync に matching loader が必要。新しい Fabric／NeoForge の共通 payload、optional channel negotiation を保持。
- player と waypoint の visibility を独立させる。payload size／UUID／座標／dimension 検証、connection/session isolation、古い client／vanilla client への未対応 packet 非送信を維持。
- Fabric の player/cave snapshot は発信元 connection を捕捉し、再接続後の旧 queued callback を拒否する。1.21.1 は相互移植作業で 1.20.1 の guard と同等に修正。[接続切替 regression](../scripts/test-server-snapshot-connection.ps1) は旧 callback が新 server の player／cave policy を上書きしないことを確認する。

### F26 — サーバー永続共有 waypoint と権限

- dedicated／LAN 公開 world で作成。LAN host は参加者がいなくても対象。authenticated creator UUID、creator face（offline も）、owner／admin／LAN host の edit/delete permission を**毎 mutation サーバー側で判定**。
- `data/odysseymap-shared-waypoints.json` に保存、再起動／後参加へ sync。最大 4096、bounded name/icon/coordinates/payload、mutation rate limit、保存失敗で旧 state を保持。
- paged snapshot は全体を atomic に apply。破損 store を保存し直して消さず、disconnect reset と permission change に追従。
- global visibility と個別 eye／favorite は player/world ごとの local preference。他人の表示・共有データを変更しない。未対応 server/client は従来接続を維持。Web に共有地点も反映。
- 共有 waypoint の構造化 INFO イベントは F43 の契約を保持する。

### F27 — Create／Steam 'n' Rails／MTR の自動駅

- 両連携を別設定（初期 on）、約 5 秒の同期 client snapshot から検出、session-only、chunk を強制 load しない。API 非互換は skip＋一度 log、名称／位置を推測しない。
- Create 0.5.1／6 の STATION graph と loaded block を検証。同名＋同 dimension を 3D 距離で transitive grouping、半径初期 50、0 は group 無効（upstream ID dedup は維持）。JSON の許容上限 1,000,000 と UI slider 上限は区別。
- MTR 3／4／4.0.5 の有効 platform rail／area／midpoint を検証。MTR は**station-area ごとに一つ**、eligible platform midpoint の幾何中央値、Create は member block の幾何中央値へ配置。矩形中心／platform ごとの marker は後に置換済み。
- identity は source＋dimension＋upstream representative UUID／station-area ID。同名遠隔駅を統合しない。name `||` 後の metadata を隠し、`|` language aliases を全角括弧＋`・` で表示。
- reflection lookup／immutable grouping snapshot の cache と change invalidation、level/logout clear、消失駅の次 refresh での削除を保持。

### F28 — 全画面の Create／MTR rail overlay

- layer menu で chunk grid／MTR／Create を個別保存。rail 初期 on、駅 waypoint スイッチとは独立。Create orange／MTR cyan、暗い outline、marker より下、実際の curve。
- current dimension の同期 graph/rail のみ。Create change を地図表示中反映、MTR 約 1 秒更新、open／logout／level change で適切に refresh/clear。rail data は disk 保存せず chunk を load しない。
- MTR TRAIN mode のみ。MTR 3 の近傍同期範囲制限を明記。source ごと 16,384 track／262,144 point、curve 最大 128 segment、viewport clip、frame 最大 8,192 visible segment。
- mouse／keyboard arrow＋Enter/Space／controller、Esc／outside click を menu に適用。

### F29 — ローカル Web map・認証・独立 viewport

- client の world 中に初期 `127.0.0.1:8901` で read-only map を host、logout/client close/mapMod disable で stop。port の別サービスを停止しない。成功後 login ごと一度 clickable local URL、設定した server public URL は vanilla player にも案内。
- 探索済み surface／active cave、player search・collapsible list・XYZ/dimension、pan／zoom／recenter、16x zoom、skin＋hat、selected icon／fallback、共有 waypoint。ブラウザーごと viewport を独立させ、ゲームの地図を勝手に移動しない。
- logout button は grid toggle に置換、browser localStorage に保存。chunk boundary 2 pixel、低倍率 terrain filtering、icon size cap、opaque immutable snapshot key、bounded image/GPU capture を保持。
- `webmap.json`、optional password と `ODYSSEY_WEB_PASSWORD`、永続 `webmap-session.key`、HttpOnly／SameSite cookie、key/password 変更時 revoke。required password 空なら host しない。scripts/styles は同梱。
- game data は client thread で snapshot 化。HTTP worker／queue／rate／request body／viewport size／icon budget を bounded にし、既存 Host／Origin／fetch-site 検査を維持。追加 hardening の未統合部分は第 5 節。

### F30 — 翻訳・ゲーム言語・Web 言語

- 現行 16 locale を全 4 root と Web で維持: `en_us`, `ja_jp`, `zh_cn`, `zh_tw`, `zh_hk`, `yue_hk`, `fr_fr`, `hi_in`, `ru_ru`, `pt_br`, `pt_pt`, `es_es`, `de_de`, `cs_cz`, `ms_my`, `ro_ro`。
- `zh_hk`（繁体中国語香港）と独自登録 `yue_hk`（広東語）を混同しない。pack metadata の登録を維持。Minecraft 選択言語へ従い、存在しない vanilla/他 MOD の広東語は fallback。
- UI／enum／tooltip／template／feedback と format placeholder の一致、UTF-8 build を維持。一般の第二言語範囲 20 言語が全て実装済みとは扱わない。
- 2026-10-28の翻訳検査フォローで、26.2のエリア編集・feedback128項目とcontroller案内設定7項目を、日英以外の既存14localeへ補完した。各135項目を確認し、実value変更は合計1884件、各既存477項目と日英612項目は保持。翻訳率50%の基準を緩めず全4root／64catalogの検査が成功。完全な旧英語fallback解消を意味しない。source参照検査はgui.openmapdelta／openmapdelta.areaとhint mode enumへ拡張した。
- 26.2 のキー割当カテゴリは `KeyMapping.Category.register(openmapdelta:keys)` が生成する `key.category.openmapdelta.keys` を全 16 locale に収録し、見出しを **OpenMap Delta** とする。旧 `key.categories.odysseymap` と各操作の `key.odysseymap.*` は互換性のため保持。Java の文字列参照検査だけでは生成 key の欠落を検出できないため、26.2 の catalog 検査で必須化する。26.1.2 の同形式への対応は P06。

### F31 — Controllable／Controllable Delta 任意連携

- 必須依存を増やさず、既存 key binding を controller MOD の設定で割当。virtual cursor と現在の button／Shift layer hint を使用する。F52対応Controllable Deltaとの全画面では再割当可能な専用default profileを使用し、旧Controllableでは汎用GUI操作を維持する。
- map drag／zoom／right click menu、list paging、完全に可視な row の star／eye／label を別 navigation point にする。menu 表示中は実際の action だけ、header／背後の widget を除外。
- reflection の旧 typed Framework register／新 register(Object) に対応、MOD 不在は静か、互換 API failure は一度 log＋failure isolation。ほかの画面の標準 navigation を破壊しない。on-screen keyboard は未実装。

### F32 — startup map MOD 選択と元データ保護

- JourneyMap／Xaero 同時導入時に title で一つを選ぶ。検出 MOD／version が変わるまで記憶、Keep current settings、Integrations から再選択。自 MOD disabled 時も設定 key は使える。
- config/cache setting の切替で制御し JAR や world／waypoint／探索済み地図を削除しない。自 MOD は mapModEnabled で map／overlay／scan／client Web host を停止。
- JourneyMap の global＋既存 world config／両 minimap preset／mapping／world marker／web、Xaero の minimap／waypoint／tracked player を実際の field 名で切替。保存失敗を表示し、保存していない成功を装わない。
- render API ごとの background blur の順序／一度だけの背景処理を維持し、title／in-game の panel／文字を鮮明に描く。

### F33 — 1.21.1 NeoForge 統合・版固有 API

- NeoForge 1.21.1 の event／network registration／refmap／metadata を完成。1.21.1 配布は Java 21／NeoForge 21.1.80 以上を要求、専用 root task `build1211NeoForge`。death は clone 前の古い player 座標を取り、new respawn 位置で代用しない。重複 death tick を除去、sleep を unload/logout reset。
- shared waypoint request は server にのみ登録／処理し、payload direction の混同による起動・接続問題を防ぐ。respawn で death marker を正しく forward、skin/item icon の色・render API 差を処理。
- Forge と NeoForge の名前が似ていても別 loader。将来「Forge 1.21.1」を実装する場合は新しい移植対象として調査・検証し、現在の成功実績を転用しない。

### F34 — Iris とワールド label backdrop

- 1.21.1 の Shader 使用時に背景が消える問題を修正。white 1px texture の `textSeeThrough` 経由の専用 backdrop を使い、alpha／geometry／fullbright／UV／buffer flush を整合させる。cluster badge も対象。
- 単なる geometry test 成功を Iris 実描画確認と同一視しない。他版へ移植するときはその版の render pipeline と shader MOD を確認。

### F35 — Deathpoints の保存・見た目・操作（1.20.1 / 1.21.1 / 26.2）

- Deathpoints／Smart Deathpoints は初期 on。死亡ごとに point、smart off は普通の赤地点。元死亡 XYZ／dimension／world/server／owner を保存し、account 切替で他人の point を表示しない。
- 赤 cross＋黒 outline、個別 world beam、数付き cluster から除外。pulse の `deathBeaconMinOpacity` 初期 `0.25` と `deathBeaconMaxOpacity` 初期 `1.0` を設定可能（0〜1）。rename／manual delete、rename で timer 維持、delete は恒久で restore／後の server report が復活させない。
- keepInventory は初期 hidden、world ごとの最後の hidden point を一度 restore 可能。toggle で常時作成可能。古い通常 death marker とは区別して統合する。
- deathpoint editor は name/category のみ変更可能。XYZ／state color／icon は read-only で resize 後も維持。設定画面は簡潔な label と説明 hover を持つ。restore は一度実行する action として表現し、成功 chat／使用後 disabled を持つ。
- client の `deathpoint-deletions.json` に deletion receipt を保存。world／owner／元死亡位置／death tick の照合で旧 report を拒否し、再接続・account/world 切替でも delete を取り消さない。`waypoints.json` と両方の保存状態を移植する。
- 1.20.1 は 2026-10-13 の相互移植作業で、1.21.1 の現行実装を Java 17／Fabric・Forge に適応して統合。旧 branch で後から置き換わった実装を直接復活させず、BED 永続化・最新 icon/navigation/rail を維持した。
- 26.2 は 2026-10-15 に現行 main から統合。GUI 抽出描画／SubmitNodeCollector と Identifier、26.2 native death/drop hook に適応。死亡地点も地図上で選択・操作可能にし、古い navigation テストの「DEATH は選択不可」を現行仕様へ訂正した。

### F36 — Smart Deathpoints の寿命・移動・回収（1.20.1 / 1.21.1 / 26.2）

- 黒の初期保持は 5 分、保持 0 は無期限。world の pause は timer を止める。
- **サーバー併用**: actual item age/lifespan を追跡し、unloaded chunk では最後の既知残時間を pause。estimated deadline だけを消失証明にせず、recoverable loot の不在を確認して黒へ変更。authoritative item timer の再検証で loot があれば赤へ戻し黒保持を解除。
- **クライアント単独**: vanilla drop の初期 5 分を world time（6,000 tick）で推定し、deadline 到達で黒へ変わる。unloaded item の真の age／消失を知ることはできず、その chunk が未ロードでも推定 timer は進む。server の実確認・unloaded pause を client-only の保証として記述しない。
- 高所 death は item gravity／slab・stairs の実 shape、水平散布／water movement、observed item/corpse の実位置、tick 間補間へ追従。未観測予測は fluid／unloaded で停止し force-load しない。元 death ID と timer は保持。
- owner が 16 block 内へ戻り、loaded 周囲で drop／grave を観測後に消失を確認した場合に chat＋remove。5 秒 grace、unloaded を空と判定しない。確認済み keepInventory empty death の visible/restored point も条件付き除去、hidden restore cache は消費しない。
- keepInventory／grave は通常 countdown 抑制、実 drop があれば開始。client-only の keepInventory 推定と、空 inventory＋XP 0 では確定不能な制約を保持。

### F37 — Smart Deathpoints の価値判定・任意 server／grave（1.20.1 / 1.21.1 / 26.2）

- 全 item が easy list 内、total count <64、level <=10 の全部を満たす軽微 death を skip。1 個でも高価／level 11／decorated（named、enchanted、dyed leather 等）なら作成。comma-separated item ID／`#tag` を設定、空 list は nonempty を valuable。
- server は true keepInventory／death inventory／actual age＋Forge/NeoForge lifespan、partial pickup／merge／他人の回収名を追跡、report は owner のみに送る、server world に保存。ordinary point は赤のまま。
- protocol の bounds／owner／world ID／順序／重複／deletion tombstone を保持。unsupported death data が codec 範囲に収まらなくても本来のゲーム死亡処理を阻害しない。
- server persistence は `<world>/data/odysseymap-death-recovery.json`。既存 file 名と world/owner scope、item／corpse／grave／collector tracking の再起動後復元を維持。
- `openmapdelta:graves` と旧 tag、grave/tomb/corpse name、server `graveBlockIds`、`corpse:corpse` を扱う。古い corpse を新しい death に結びつけない。static grave はその loaded block の消失まで記憶。
- Corpse `force_time` -1/0 は full body 無期限、skeleton_time は見た目だけ、empty body time は loot 寿命延長ではない。forced spill 後は実 item UUID／merge／lifespan を追跡。API 不明時は推測 countdown を出さない。
- right click Ask for help、3 種の強調 template、初期 5 分、singleplayer local notice、sky/void death の位置共有を保持。teleport の build height 制限とは別。

### F38 — 最後の適格な睡眠地点の永続化

- `config/openmapdelta/waypoints.json` に BED type と exact bed height を current world/server ごと保存、再入室で復元、同 world の次元を跨ぐ睡眠は既存一つを移動、他 world の地点を保持。
- red bed item を専用初期 icon とし、list に表示、name/color/icon/category/XYZ/visibility/favorite を編集可能。後の睡眠・再接続で UUID と好み（明示的な icon 空を含む）を保持。Show Beds は通常 waypoint と別 gate、非表示でも記録を止めない。world icon/beam に通常 world setting を適用。
- `recordNonSpawnSleep=false` が初期。成功 sleep state のみ観測、spawn を設定しない寝袋等は opt-in。非適格 sleep は前の地点を消さない。singleplayer は実 server respawn point、remote は vanilla bed rules＋standard spawn confirmation を保守的に使用。
- 失敗した bed interaction、anchor の charge／empty／explosion、unrelated／交互／遅延 spawn notification、起床前後の順序、hand rule、stale evidence／logout clear、変わらない既存 spawn を区別。server 導入を必須にしない。

### F39 — 数値設定の slider 化・設定互換

- numeric settings と duration を slider へ置換、drag／左右 arrow single-step。秒/tick・駅 group 等の広い範囲は log scale。画面を開いただけで保存済み値を丸め直さない。
- waypoint XYZ と hex color は正確な直接入力を維持。finite/range の validator、版固有 map 設定の保存、旧 icon 設定の継承、無関係な設定の保護を維持。
- root／nested map JSON の backward-compatible 読込と版固有 default/serialization を維持。common classes を全 root へ機械コピーして形を揃えない。zero／`-1` の sentinel の意味を slider で失わない。
- 1.20.1／1.21.1／26.2 の死亡地点カテゴリー（F54より前はDeaths tab）も slider を使用。黒保持 0〜86,400 秒（1 秒 step、log scale、0 は forever）、beam opacity 0〜1（0.01 step、percent 表示）、既存値と hover 説明を維持。以前の death 専用 cycle button を復活させない。
- 26.2 の flat JSON、world marker 上限の初期 200／範囲 10〜2000、safe render 初期 true は既存の版差として保持。1.21.1 の設定定義で一括置換しない。
- 1.21.1 の `infoCornerAlignment` は省略可能な client enum 設定で、初期 MAP_EDGE／選択 CENTER。保存は flat root、読込は root と nested `map` の両方（nested 優先）を許容し、不正文字列／型／null は MAP_EDGE へ戻す。

### F40 — Portable builds and distribution

- Each version root is an independent Gradle project. Match its Java and loader metadata when building.
- Keep the Gradle wrappers, required assets, service registrations, mixin metadata, LICENSE and NOTICE files.
- Distribute the completed loader JAR, excluding sources, Javadoc and intermediate development artifacts.
- Test changes with relevant compile checks and targeted regression scripts. State verification limits in the pull request.
- Local commit identities, signing, worktrees, launcher instances and deployment automation are contributor preferences, not repository requirements.
### F41 — biome grass tint と非同期 map generation

- `biomeGrassTint` 初期 on、Nether/End 以外の grass/foliage を biome grass color にし、元 pixel も残して即座に設定切替。旧 cache は rescan で enrichment、探索履歴を消さない。
- pixel channel は 1.20.1／1.21.1 が ABGR、26.1.2／26.2 が ARGB。sampler と版固有 NativeImage API を揃え、版間コピーで逆転させない。
- `asyncMapGeneration` 初期 on。CPU load 80% 未満・spare core 時に最大 4 worker／2 core を予約し、busy 時は live client-thread scan に fallback。surface/cave は bounded worker を共有。
- worker は copied palette/snapshot だけを計算し、world-dependent callback、cache mutation、render を off client thread で実行しない。chunk revision／session/dimension 変更で stale result を拒否、繰返し invalidation は live に fallback、logout で stop。
- テレポート時は map の pan 位置より player 近傍を優先し、近傍未受信 chunk を後で再試行。F12 の disk I/O 非同期とは別の設定／責務。

### F42 — 権限付きサーバー設定画面・即時適用（1.20.1 / 1.21.1 / 26.2）

- 設定の「連携」からサーバー設定を開く。OP level 2 以上、または singleplayer／LAN owner が対象。サーバー側で変更ごとに認可し、権限の付与／剥奪を接続中も同期する。未対応サーバー／一般参加者には編集を許可しない。
- `server.json` の `allowCaveMap`、`webMapEnabled`、`webMapUrl`、新規 `maxSharedWaypoints` を再起動なしで保存・適用。件数は slider と数値入力を併設し、URL／件数入力は短い debounce、トグルは即送信。保存成功の応答後に確定表示する。
- Web 設定はサーバー運営の公開 URL のチャット案内。新たな公開／URL 変更を現在の参加者にも即時案内する。実際の HTTP listener／password／session secret は従来の client `webmap.json` が所有し、この管理 protocol では取得・変更しない。
- 共有地点の上限は初期 4096、範囲 0〜4096、ワールド全体の作成数。0 は新規作成停止。上限を下げても既存地点を削除せず、編集／削除を継続可能。既存 client の作成要求にもサーバーで適用する。
- 洞窟の変更は既存の cave policy snapshot も直ちに再配信し、全画面／Web の制限へ反映する。設定管理 protocol 非対応の既存 client にも既存 policy envelope で配信する。
- 専用 optional C2S／S2C channel、8192 byte 上限、厳密な型／URL／件数／末尾検証、更新 revision による stale 管理者の上書き防止、rate limit、接続 token の照合と logout reset を維持する。公開設定値は管理可能な参加者だけへ送信する。
- atomic 保存が成功してから runtime を更新し、失敗／競合は旧値を維持。既存未知 key と `graveBlockIds` を保持し、壊れた policy は上書きせず編集停止。保存・共有 waypoint 形式と旧通信 envelope は維持する。

### F43 — 共有 waypoint 変更成功の構造化 INFO イベント

- 既存 `OpenMapDelta` logger の INFO に、固定 prefix `OPENMAPMC_EVENT ` と compact JSON を出力する。schema_version=1、event=shared_waypoint_created／shared_waypoint_updated／shared_waypoint_deleted、waypoint_id／creator_uuid／creator_name／name／dimension／x／y／z を含む。creator は waypoint の保存済み所有者、座標は保存済み整数値を JSON number とする。CREATE の既存 10 field は維持する。
- UPDATE／DELETE には authenticated server actor の actor_uuid／actor_name を追加し、他人の地点を扱う管理者と所有者を混同しない。UPDATE は保存成功後の状態、DELETE は削除直前の保存済み状態を使う。各 mutation の atomic 保存と server state 公開が成功してから 1 回だけ出力。拒否／不正入力／rate limit／上限／保存失敗、restart load／snapshot、個人 waypoint では出力しない。
- Gson による文字列エスケープと既存 CR／LF 入力拒否を維持し、ユーザー文字列からログ行を追加させない。packet／world store／config の形式は変更しない。通知の外部送信処理は本 project の対象外。

### F44 — ネザー／天井のある次元の実地形 surface

- ネザーの地表表示を土／石の疑似模様に置換せず、天井の下にある実ブロックと溶岩の色・高さを採る。天井より上の建築を除外し、1 block の小穴を主空洞と誤認しない。
- live client-thread と detached worker の採取結果を一致させる。通常次元と明示した洞窟レイヤーの契約を維持。保存形式の変更や cache の一括削除をせず、既存の loaded-chunk 再走査で更新する。
- 関連: F09／F11／F12／F41。全 4 版で実装・headless 回帰検証済み。native build／実 Minecraft 描画／配置の確認範囲は下記作業記録を参照。

### F45 — ミニマップの独立した洞窟レイヤー選択

- 全 4 版で地表固定（既存設定の初期値）／プレイヤー Y 追従／手動 Y を選択・保存する。自動／手動とも F11 の caveLayerStep と次元の最低 Y に整列し、次元範囲に収める。
- Y 追従は loaded・primed な WORLD_SURFACE の最高 non-air Y が floor(eyeY) より低い露天で Surface を選ぶ。昼夜・天候・skylight 値に依存しない。glass／leaves／fluid 等も遮蔽物とし、hasCeiling 次元は洞窟帯を維持する。未受信 chunk／未準備 heightmap を露天と扱わず、要求・生成しない。手動指定／全画面／Web の独立選択は維持する。
- 全画面／Web の選択とは独立。サーバーが洞窟を許可しない間は地表へ戻し、保存した希望モードは保持する。同じ洞窟帯を表示するときは cache／scanner を共有して同一ファイルへの二重 writer を作らず、異なる帯では固定 2 組を再利用する。
- 設定キーは `minimapLayerMode`（SURFACE／PLAYER_Y／MANUAL）、`minimapCaveY`（初期 64、整数 -4096〜4096）。Map Settings → Minimap からモードと手動 Y を変更・即時保存する。UI の手動 Y は現在の次元範囲、実際の採取帯は caveLayerStep に snap する。日本語／英語のラベルを用意し、他の既存言語 catalog に英語 fallback key を保持する。
- 2 組の洞窟 cache は同じ serial disk executor を共有し、別時刻に同じ帯の所有 slot が変わっても旧 final write より後に新 load を処理する。session／dimension／sampling epoch の既存隔離を維持する。
- 関連: F03／F11／F12／F41。全 4 版で実装・headless 検証済み。F11 の旧「ミニマップは surface のまま」を任意選択へ拡張した。native build／配置／実描画の確認範囲は下記作業記録を参照。
- 洞窟帯への切替時は直前が Surface の場合も render-only の直前 raster を最長 1,000 ms 保持し、未取得の部分が残れば 200 ms で fade して破棄する。新しい帯の観測済み pixel（透明を含む）から即座に置換し、保存 tile には旧帯の値を混ぜない。zoom 中の移動も nearest sample で world 座標に再配置。連続切替で元の保持期限や alpha を延長・乗算しない。Surface／policy 禁止への戻り、session・次元変更、zoom 変更、texture clear は保持を破棄する。

### F46 — 外部マップの遠隔プレイヤー連携

- 全 4 版で `externalPlayersEnabled` 初期 on。JourneyMap／Xaero の画面・overlay を F32 の選択で無効にしても、導入済み MOD が受信した遠隔位置を読む。JourneyMap 5／6 の confirmed `journeymap.common.util.PlayerRadarManager.getInstance().getPlayers()`、Xaero 25.3.13／26.4.2 の synced store と registered tracker systems（Open Parties and Claims 等）を public reflection API で取得。Minecraft の player／dimension API は typed adapter を使い、Fabric remapping に依存する getter 名を反射しない。
- JourneyMap／Xaero の JAR 自体を無効化／削除した場合は同 store を取得できない。それらの private network protocol を独立実装したとは扱わない。新しい OpenMap server の導入なしで**既に公開・配信されている位置**を読む機能であり、vanilla server が送信しない遠隔座標を推測・取得しない。配信元の hidden／foreign／profile 制限を保持する。
- BlueMap は他の client map MOD 不要。設定 → 連携 → BlueMap のプレイヤー位置から、公開 HTTP(S) website URL／map ID／相対 live-data root（初期 `maps`）を現在の server address／dimension に明示的に結び付ける。map 名から dimension を推測しない。設定は `config/openmapdelta/remote-players.json` schema 1、最大 256 binding／256 KiB、同一 file 内でも server・dimension を隔離。破損 file を暗黙に上書きせず、atomic save 成功後だけ状態を更新する。
- `maps/<map-id>/live/players.json`（custom relative root も指定可能）を約 3 秒ごとに background 取得。`foreign=true` を除外し、座標を block floor、UUID／name／dimension／finite coordinate／重複を検証。最大 4096 player／800,000 byte、redirect／credential／query／fragment 付き URL を拒否し、6 秒の全体期限と 3 秒の socket timeout、明示的な active connection cancel を適用する。
- JourneyMap／Xaero は約 1 秒ごとに complete store snapshot を読む。source 別に全置換し、公開一覧から消えた player は次の成功取得で削除。外部 snapshot は最大 15 秒で失効し、installed store の成功読取も期限を更新するため、store 自体の鮮度は配信元の管理に従う。logout／level identity／connection identity／連携無効化で clear、古い worker の完了結果を新 session に適用しない。
- native OpenMap server snapshot が一度でも受理された接続では、その完全な player 一覧（空も含む）を優先。外部 state から native cave policy を変更しない。native 不在時は duplicate UUID に JourneyMap > Xaero > BlueMap、loaded entity は既存どおり毎 tick 優先。F04 の最寄り情報、F22 の追跡、minimap／fullscreen player marker、F29 の local Web map に同一取得経路を使う。26.1.2 の world beam 等、P01〜P04 はこの連携で移植済みとは扱わない。
- UI の追加項目は日英、他の既存 14 locale は英語 fallback。既存 protocol channel／world data／waypoint／cache／他 MOD の設定保存は変更しない。反射の confirmed class／固定 method table を cache し、optional API 不在・iterator の RuntimeException／LinkageError を game loop から分離する。
### F47 — ミニマップ内外の他プレイヤーラベル選択

- ミニマップ内と外で他 player の label を別々に選択する。選択肢は NAME_ONLY（名前のみ）／DISTANCE_ONLY（距離のみ）／NONE（どちらも表示しない）／NAME_AND_DISTANCE（名前と距離）。初期値は内 NONE、外 DISTANCE_ONLY。旧 config に新しい key がなくてもこの初期値を使用する。
- 実地図の circle 半径／square 半幅と、回転前の world→terrain 相対座標で分類し、正確な境界は内側とする。地形自体が heading-up で回転するため、表示後の固定軸に対する判定へ置き換えない。clamp 後の icon 位置、marker edge offset、inView フラグを内外判定に使用しない。
- 対象は renderHud の他 player の label。選択して追跡中でもこの設定を優先し、icon と halo は維持する。SHOW_MARKER_DISTANCE と独立。自分／その他 marker／fullscreen／world／Web の表示は変更しない。F05／F06／F22／F46 に関連する。
- 設定 key は `minimapPlayerLabelInside`／`minimapPlayerLabelOutside`。flat 保存、legacy nested map spelling の読込・nested 優先、大小文字を許容する enum 読込、不正な型／値の個別 default、旧 file の key 不在時の初期値を保持する。設定 → ミニマップの「地図内プレイヤー」「地図外プレイヤー」で即時保存する。

### F48 — 近傍エンティティの小型 MOB アイコンと高低差フェード

- 全 4 版で loaded entity をミニマップ／全画面の小型アイコンとして表示。既存 MOB face catalog を entity type に対応付け、顔がない種類は小さい色 marker に fallback。プレイヤーとの Y 差を 4 block ごとに 100／80／60／40／20% の 5 段階とし、16 block 以上も 20% を残す。
- 2026-10-28の初回調査では固定vanilla23種・魚未登録・縦横比変形を確認したが、追加指示で**26.2のみ**モデル画像生成へ拡張した。既存専用画像を優先し、魚4種とcatalog未登録のMOD等は登録rendererからhead優先・取得不可／失敗／空画像なら全身を生成する。安全なroot直下headのみを使い、独立layer／未知骨格・variant切替のheadは無理に推測しない。魚は生成待ちも魚種別画像、他の未生成種は色記号へfallback。2D画像の縦横比を保持する。[仕様・制約](entity-icon-rendering.md)／初回調査／TDD根拠を参照。他版の固定画像方式まで修正済みとは扱わない。
- 26.2の生成画像は最大128件、100ms間隔・pending2件・失敗待ち5秒。vanilla魚の外見keyを共有し、MOD subclassはUUID等で分離。未知個体は約10秒後に旧画像を表示したまま更新し、未生成を優先するがcooldown中の失敗個体で更新を阻害しない。world／reload世代で古い結果を拒否し、texture解放は2tick遅延。生成時のみ非同期readbackでalpha検査・cropし、通常表示はcache再利用。renderer modelと送信されたmodelのpose／scale／visible／skipDrawを復元する。
- 「設定 → マーカー → エンティティを表示」で切替。**26.2 の初期値は true**（2026-10-25 の表示要求で旧 false を置換）、他3版の初期値は false。既存の保存済み true／false は維持し、旧既定値による false とユーザーの手動 OFF を識別できないため、一律の強制 ON 移行は行わない。今回の26.2テスト環境は要求に合わせて個別に ON へ変更する。クライアントにロードされた 3D 距離 64 block 内という収集範囲を維持する。全画面では waypoint 表示 toggle／件数枠から分離し、通常 entity の自動 label は両画面で省略する。プレイヤーの icon／label／waypoint 保存・通信の仕様を変えない。
- 26.x の vanilla texture 改名・サイズ変更も実資産と照合して修正。entity は player／waypoint より先に描いてアイコンを隠さない。F05／F06／F16 に関連。根拠: `marker/EntityMarkerStyle`／`MarkerManager`／`MarkerRenderer`／`MarkerIconRenderer`／`MarkerIconCatalog`、[style・実 renderer capture](../scripts/test-entity-markers.ps1)、[実 MOB 資産照合](../scripts/test-mob-icon-resources.ps1)、[利用方法](multiplayer.md#nearby-entities)。native build・配置・実機未確認の区別は末尾記録を参照。

### F49 — 縮小時の物理画面解像度を保つ terrain raster

- 全 4 版で全画面／ミニマップの描画密度を GUI pixel 基準から物理画面 pixel 基準へ改善。ユーザーが選択した「画面の実解像度を保つ（負荷を抑えながら精細化）」を実装し、設定の追加操作なしで適用する。
- terrain cache の 1 block データを変更せず、同じ表示範囲／倍率／world→marker 座標を保つ。GUI scale を考慮し、1 block より細かい偽の情報は生成しない。full screen は上限 4096 texel（overscan 含む）、minimap は 2048 texel 以内とし、極端な画面／倍率でも無制限にメモリを増やさない。
- 全画面の非整数 sample footprint は面積重み付き平均で扱い、ミニマップの高密度 raster でも F45 の Y 帯保持／期限／policy／zoom 時の破棄を維持する。画像再利用・変更範囲コピーで負荷を抑え、可変サイズ texture の置換／clear 時は登録先 TextureManager から旧登録を解放する。F03／F09／F12／F45 関連。

### F50 — 高さの変わる境界の地形陰影

- 全4版のミニマップ／全画面で、隣接する既知 block の高さが変わる境界に陰影を付け、高低差を読み取りやすくする。低い側を暗く、高い側の縁を控えめに明るくし、固定の北西光源を基準にする。高さ差1〜8 blockで効果を強め、8以上で飽和させる。合成後の明度倍率は0.55〜1.12に制限し、平坦な場所は元色のままにする。
- 保存済みの height／表示色から描画時に導出し、raw pixel／height／grass／tile 保存・config・protocol を変更しない。高さ不明・透明・未取得の隣接 block を崖とみなさず、タイル境界の既存 neighbor の高さを参照する。neighbor の追加／高さ変更／削除でも描画と平均色を更新する。全画面は平均化前に陰影を適用し、F49 の物理密度・bounded cache と F45 の一時保持を維持する。
- 関連 F09／F11／F12／F41／F45／F49。Web の pixel export は本件では変更しない。根拠: `world/TerrainRelief`／`TileColorAverage`、`render/FullscreenTerrainRaster`／`MinimapTexture`。[陰影仕様](map-rendering.md)、[relief test](../scripts/test-terrain-relief.ps1)、[raster test](../scripts/test-fullscreen-map.ps1)、[実textureの回帰](../scripts/test-minimap-texture-transition.ps1)。全4版のpixel検証・全8配布buildと3開発配置は完了。実GPU／FPS未確認などの限界は末尾記録を参照。

### F51 — 全画面の自プレイヤー頭の近接縮小・詳細ズーム（26.2）

- 2026-10-27 の新要求: 全画面の自プレイヤー頭を通常約25%縮小し、カーソル接近時は段階的に1 world block幅まで縮小して周辺へのマーカー配置を妨げないようにする。描画と選択範囲を一致させ、自プレイヤー中心の選択を維持する。全画面の詳細ズーム上限を2x→4xへ拡張する。
- 通常sizeは既存head sizeの75%へroundし、従来capと通常の最小4GUI pixelを保持（capが小さければcapを優先）。cursor distanceを通常size基準で扱い、1.5倍以上では通常、0.5倍以下では最小、その間を4段階にquantizeする。最小は `ceil(1 / blocksPerPixel)`、ただし縮小表示では最低1GUI pixel。初期cap16の4x表示では通常12→10→8→6→4GUI pixelとなり、最小4pixelが1world blockに相当する。cursorを離すと戻る。前frameのhover／sizeへ依存せず、同じcursor位置で振動しない。
- 描画とcontext選択は同じmouse座標（layoutではGUI整数へfloor）からsizeを求める。headの中心は選択でき、旧sizeの見えないhit領域は残さない。回転するheadには回転角を包含する対称envelopeを使用し、skin未取得／失敗時も同size／中心／回転の色付きsquareを描く。画面外edge headは通常sizeのままとし、既存の可視性／waypoint重複時の非表示／marker優先順位は維持する。
- `FullscreenMapZoom` の最低0.25bppと両方向のhalf／double stepを使用し、terrain／grid／rail／marker／右clickのworld座標へ同じ倍率を適用。1blockより細かい偽のterrain sampleを追加せず、既存のtexture上限／cacheを維持。既存のmarker iconの成長上限、label threshold設定範囲0〜2、相対default zoom整数1〜18は変えない。
- 26.2のみ実装・headless／実RenderUtil capture／両loader build検証・NeoForge配置済み。実GPUの表示／操作は未検証。他3版／ミニマップ／Web／他プレイヤーの通常サイズは変更対象外。F05／F09／F10／F16／F21／F49関連。根拠: `marker/FullscreenLocalPlayerSizing`／`MarkerRenderer`／`render/FullscreenMapZoom`／`util/RenderUtil`、[近接・選択geometry](../scripts/test-fullscreen-player-proximity.ps1)、[zoom／raster](../scripts/test-fullscreen-map.ps1)、[実RenderUtil capture](../scripts/test-player-pointer-render.ps1)。

### F52 — 全画面専用の Controllable Delta 入力 profile

- 2026-10-27要求: LS cursor、L3／LT右click action、RS map pan、R3 recenter、DpadRightでwaypoint list開閉＋focus、Left未割当、Up／LB拡大、Down／RB縮小（popup／list focus中は上／下選択）、RT左click相当confirm、STARTで全画面mapを閉じる。LB／RBのmenu方向はユーザーの明示回答「LB＝拡大・上、RB＝縮小・下」で確定。
- F31の汎用GUI操作を全画面に限定した再割当可能な既定profileへ拡張する。Controllable Deltaが物理入力／deadzone／repeat／Shift／汎用GUI入力との重複抑止を担当し、OpenMapがsemantic action／list・popup選択／focus座標を担当する。旧Controllableと非map画面の既存連携は維持する。
- OpenMapの全4rootにJDK型だけの公開API `getControllerProfileId()`（`openmapdelta:fullscreen`）／`handleControllerAction(String,double,double)`／`panControllerMap(double,double)`／`getControllerFocusPoint()` を追加。popup→focused list→mapの順で上下操作を振り分け、popupのheader／隠れた行／画面外pointを除外。contextはHUDが可視・popupなし・地図内の場合だけnative menuを開いてconsumeし、confirmとその他のcontextはControllableの既存mouse emulationへ委譲する。
- 一覧focusは→で開く操作と有効なrow clickで有効になり、実Screen／widgetのfocusを揃える。Dpad移動はhover対象だけを変え、RTのnative clickまでwaypoint選択／recenterを実行しない。UUID／categoryでrow identityを保持し、filter／collapse／定期refresh／resize／F1で適切に復元・clampする。mouse wheel／page移動はfocus行を新しい可視pageへ合わせ、refreshで古い行へsnap-backしない。list外click・popup／overlayへのfocus移動・list閉鎖でlist modeを解除し、同一行のnative doubleclick編集を維持する。
- RS panはGUI pixel入力を現在のblocks-per-pixelでworld差分へ変換（+X east、+Y south）。list focus中もpanでき、popup中はconsumeのみ。NaN／infinity／overflowをcenterへ伝播しない。STARTはpopupだけでなく全画面map本体を閉じる。Controllable側はcontext／list／menu移動後に返されたfocus座標へcursorを合わせ、RTの決定対象を表示中の行と一致させる。
- 専用profileの18bindingはControllable Deltaの `controllable.key.openmap.*`。hintは実registryのactive button（Shift1／2・remap・明示unbound・alternate）を読み、旧GUI hintを誤表示しない。profile導入時の日本語／英語7hint keyと他catalogの英語fallbackを保持。新必須依存・network／waypoint保存形式は追加しない。
- 2026-10-28の追加要求で26.2のみ、案内を初回8秒のFIRST_TIME既定へ変更。実際に表示できた時だけcontrollerHintsSeenを既存client configへ保存し、次回map／world／game再起動では再表示しない。設定→連携でALWAYS／NEVERへ変更でき、初回モードへ戻す／再表示ボタンで既読と親map sessionをresetする。新controllerHintMode／controllerHintsSeenはclient側の追加key。キーボード・未接続・F1・menu・表示領域なしでは初回を消費しない。
- 26.2のボタンはControllableのClientHelper.getButtonComponent(int)から実buttons.png font componentを取得。ControllerSupportの任意反射連携を保ち、theme・Shift・remap／unboundと整数IDによる重複除去を反映する。styled幅制限と描画でfont styleを保持し、画像を文字名へ戻さない。Controllable本体／資産を複製・変更せず、旧GUI bindingもglyph化する。他rootの旧hint方式は未変更。根拠: [案内policy](../scripts/test-controller-hint-session.ps1)／[画像描画](../scripts/test-controller-hint-render.ps1)／TDD根拠。
- OpenMap側は全4版の実装・policy／hint・両loader build確認済み、actual widget fixtureは26.2のみ。組み合わせる今回のControllable Delta checkoutは26.2のみで、同版の新profileは両MODを配置済み。他版へControllable側の新入力まで移植済みとは扱わない。実device／Minecraft入力・GPUは未検証。根拠: `gui/FullscreenControllerPolicy`／`FullscreenMapScreen`／`MarkerListWidget`、`compat/ControllerSupport`、[操作](controller-support.md)、[policy](../scripts/test-fullscreen-controller.ps1)、[実widget](../scripts/test-controller-widget.ps1)、[実hint adapter](../scripts/test-controller-hints.ps1)。

### F53 — 任意の地表地名・領域と階層の編集（26.2・初期ON）

- 2026-10-28要求: 地表の池・川・海・陸の命名、分岐する川、陸の区分、国の中の地域。最初は設計のみだったが、追加指示で **本体統合・デフォルト有効・エリア描画ボタン・26.2のみ・個人用とサーバー保存の選択** を確定した。当初の初期OFF／共有は後段という提案は置換済み。他3rootと1.21.11へ対応済みとは扱わない。
- 全画面の「エリア描画」から、名前のみ／分岐線／複数面と穴、名前と形状の独立、枝の別名分離、国→地域の親検索・分割、同じ親の地域の共有境界、結合、点の追加／削除／移動、座標入力、Undo/Redo、明示保存／取消を利用できる。通常閲覧と編集の入力範囲を分け、scroll可能な操作欄と固定保存欄を持つ。全下書きを描画し、共有保存の確認待ちは編集を停止する。
- `areasEnabled=true`、`minimapAreasEnabled=false`。無効化して個人storeの読込・書込と専用描画／編集を停止しても保存データは削除しない。全画面とミニマップの地表判定はF45の各実layerを使う。面の塗りは初期OFF、名前・境界・表示は個別設定。minimapは任意の正立label最大5件、circle／squareの全文字範囲とplayer中心を避ける。terrainへ焼き込まず、bounded scanline cacheとlabel衝突間引きを利用する。
- 個人用は `config/openmapdelta/places/<SHA-256(world identity)>/areas.json` に保存し、サーバーへ送信しない。共有は内蔵／dedicated serverの `data/openmapdelta-areas.json` に保存し、新しい任意channel `openmapdelta:areas` で対応clientへ同期。schema v1、scope／owner／parent／形状／style／revisionを保持し、world UUID・接続世代・expected collection revision・全変更対象と新しい親へのowner/admin権限を検証する。disk-first atomic保存、厳密UTF-8とfield／size上限、分割snapshot一括反映、固定headerだけの制限応答を持ち、破損／未来形式／不正scopeで元fileを上書きしない。
- 上限: 各保存先worldで256area／合計16,384point、1area512point・32part、共有mutation32変更・本文30KiB、JSON8MiB。sharedのsync／mutationは別20tick budget。UI日英、既存の他14catalogは英語fallback。通常waypoint／world identity／terrain／既存channelの互換識別子・保存形式は変更しない。
- MapFrontiersはUI／構成の参考で、今回そのsource・画像や新必須MODを取り込んでいない。自動水域抽出、chunk塗り、MapFrontiers import、Web area、入退域通知と倍率／priorityの詳細編集UIは未実装。関連F08／F12／F13／F15／F21／F26／F30／F44／F45／F49／F52。残る拡張と他版移植はP08。

### F54 — 設定画面の標準サイズ・カテゴリーヘッダー・スクロール

- メイン設定画面は、項目数に応じてボタンの高さを縮める固定グリッドを廃止し、標準20 GUI px高のボタン／sliderを幅広い1列の一覧にする。画面幅に応じて幅を制限し、scrollbarの場所も確保する。
- 既存の「ミニマップ／マーカー／全画面／パフォーマンス／連携／死亡地点」の翻訳をカテゴリーヘッダーに再利用する。26.1.2はSmart Deathpoints未統合P02により5カテゴリーを維持する。
- native `ContainerObjectSelectionList`でスクロール・クリップ・キーボードfocus・narrationを扱い、タイトルとインポート／完了footerを一覧の外側へ固定する。子画面から戻る／resize時のscroll位置を保持する。
- キーボード利用時にscroll位置を復元する場合は、画面内の完了ボタンへ初期focusを置き、native初期focusによる先頭への巻き戻しを防ぐ。1.20.1は背景描画を維持し、固定位置だったnative scrollbarを行右端の外へ補正する。他版へこの旧API固有補正をコピーしない。
- 枠色のhex直接入力／swatchはパフォーマンス内、安価な死亡itemの直接入力は死亡地点内にlabel付きで配置する。各版の設定項目・active条件・即時保存・tooltip・既存値を維持し、設定／保存／通信形式を変更しない。


## Known porting gaps

- P01: Selected-player world icons/beacons remain unported to 26.1.2.
- P02: Smart Deathpoints remains unported to 26.1.2.
- P03: Additional web API hardening proposals remain separate from the existing Host/Origin/rate protections; evaluate changes against the current code and protocol.
- P04: Global physical-pixel text alignment is incomplete. The 1.21.1 minimap information fixes do not imply coverage of every GUI, CJK font or world label.
- P05: The initial 1.21.1 feature-parity comparison was completed; recheck the stable requirement IDs when porting later changes.
- P06: The 26.1.2 generated keybinding-category heading still needs its matching translation key.
- P07: No 1.21.11 source root or compatible distributable is included. Do not substitute a 1.21.1 JAR.
- P08: Surface place names/area editing is implemented only in 26.2. Other-version ports, automatic water drafts, web editing, external imports and additional editor controls remain future work.
- P09: Extended fish/mod-entity icon generation is implemented only in 26.2. Special renderers, deeper head extraction, provider APIs and other-version ports remain limited or unimplemented.
- P10: The 26.2 emoji text compatibility fixes require the matching Emojiful Delta display API. Other-version ports and actual GPU/physical-input/IME verification remain incomplete.

### F55 — Public repository privacy and contribution setup

- Publish the current project with a fresh Git root and retain source, tests, wrappers, licenses and attribution.
- Exclude personal agent instructions, IDE launch profiles, credentials, local workspaces, generated outputs and private historical evidence.
- Keep build and contribution instructions portable. No contributor is required to use a maintainer's account, SSH key, launcher instance, agent tool or automatic deployment workflow.

## Public snapshot note

2026-11-02: Prepared OpenMapMC Delta for publication at https://github.com/BlueShapes/OpenMapMC-Delta with a new root commit. Preserved the runtime implementation and compatibility identifiers while separating private operational history from this functional register. The previous fork history is not imported. Publication changes do not require a new Minecraft runtime build or deployment.

Publication checks: independent privacy review passed and Gitleaks reported zero findings in the staged public snapshot. Compared 1,759 runtime/build/test/script blobs with the source snapshot; only an opaque synthetic migration-test value was replaced with an explicit test sample. The targeted data-directory migration runner passed 42 assertions; its Windows junction and ACL cases were skipped by the test environment. Retained all licenses, third-party notices and five Gradle wrapper JARs, and enabled executable modes on the Unix wrappers. No Minecraft instance or saved data was changed. Initial commit message: `Initial public release of OpenMapMC Delta.`

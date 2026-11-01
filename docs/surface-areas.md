# 地表の地名・エリア / Surface areas

対象は **Minecraft 26.2 の Fabric / NeoForge**。他のソースルートには移植していません。
初期状態で有効です。全画面マップの「エリア描画」から利用できます。
自動で地形を判定して命名する機能ではなく、利用者が名前と形を編集する機能です。

## 保存先

| 選択 | 保存と公開範囲 |
| --- | --- |
| 個人用（この端末） | この端末だけに保存します。サーバーへの送信や他プレイヤーへの公開は行いません。サーバー側の導入は不要です |
| 共有（サーバー） | 対応サーバーのワールドへ保存し、同じサーバーの対応クライアントへ共有します。内蔵サーバーのあるシングルプレイでも利用できます |

個人用が最初の選択です。保存先を切り替えても、既存の個人用エリアを自動公開しません。
未対応サーバーや同期が利用できない接続では、共有への作成・保存は行えません。
共有エリアの変更・削除は作成者または管理者に限定します。
新しく親エリアを指定する場合も、その親の管理権限が必要です。
同じ境界を共有する他人の地域まで変更する操作には、その全対象への権限が必要です。

## 基本操作

1. 全画面マップを開き、「エリア描画」を選びます。エリアの編集は地表レイヤーで行います。
2. 保存先、名前、種類、形状を選び、「新規」で下書きを作ります。
3. 「形状を描く」で地図に点を置き、「完了」で線や面を確定します。Enterでも描画を完了できます。
4. 名前・親・色・表示設定の変更は「反映」で下書きへ適用します。
5. 「保存」で選択中の保存先の下書きを確定します。「取消」で戻るときは、未保存の変更を破棄するか確認します。

編集欄の一覧と操作欄は別々にスクロールできます。保存・取消は下部に残ります。
個人用と共有用の下書きは別々に保持されます。片方を保存しても、もう片方を保存済みにはしません。
共有の確認待ちは編集を停止し、拒否・競合・保存失敗では下書きを保持します。
応答が届かなかった場合、保存済みかを確認してから再試行してください。

形状は次の3種類です。

- **名前のみ**: 地名の位置だけを保存します。領域内判定はありません。
- **線**: 川や水路等を表します。「枝を追加」で同じ名前に複数の線を持たせられます。
- **面**: 池・海・陸・国・地域等を表します。「別の面を追加」で飛び地や島、「穴を追加」で除外部分を作れます。

面は3点以上、線は2点以上が必要です。自己交差する面や、外側の面に収まらない穴は確定できません。
「名前を移動」は名前の位置だけを変更し、川筋や境界は動かしません。
「点を追加」「点を削除」や頂点のドラッグで形状を編集できます。選択した点はX・Z入力でも移動できます。
地図の移動には中ボタンドラッグを使えます。
Undo/Redoは下書きの編集に作用し、保存済みデータを自動で書き換えません。

## 川と地域の編集

川の分岐に別名を付ける場合は、分けたい枝の頂点を選び、新しい名前を入力して「枝を別名に分ける」を使います。
元の川に少なくとももう一本の線が残る場合に利用できます。流れの方向、本流・支流は自動判定しません。

国・地域を作る場合は種類を「国」「地域」にして、検索で親を選べます。
「地域に分割」は、入力した2地域名と地図で選んだ2点を通る直線から子地域を作ります。
親の面はそのまま残り、子地域は親の範囲を分け合います。
同じ親を持つ地域の共有境界は一括して編集します。
結合時は残す名前を確認し、削除時は子エリアを親なしへ移します。
「国」「領土」といった名前に土地保護・建築権限等のゲーム上の効果はありません。

## 表示・設定

設定の全画面マップ欄に次の選択があります。

- 「エリア描画・地名」: 初期ON。OFFにすると個人用データの読込・編集とエリア描画を停止します。保存データは削除しません。
- 「ミニマップのエリア名」: 初期OFF。地表表示中だけ、読める範囲の地名を最大5件表示します。

エリアごとに地名・境界・塗り・表示を切り替えられます。塗りは初期OFFです。
エリアはウェイポイントと独立し、目的地、ワールド内ビーコン、チャット通知へ自動追加しません。
地形テクスチャへ名前や境界を書き込まないため、表示を切り替えるための地形再走査は不要です。

## 保存と制限

- 個人用: `config/openmapdelta/places/<world-identityのSHA-256>/areas.json`。
- 共有: ワールド内の `data/openmapdelta-areas.json`。新しい任意チャンネル `openmapdelta:areas` で同期します。
- 地名は128 Unicode code points以内。各保存先のワールド全体で256エリア、1エリア512頂点、合計16,384頂点が上限です。
- 共有は1回の保存で32件の変更、送信本文30 KiB以内です。大きな変更は複数回に分けて保存します。
- 共有は版番号を照合します。他の人の保存と競合した場合、古い下書きでサーバーを上書きしません。
- ワールド識別、未来の形式、破損、保存失敗では既存ファイルを空データで置き換えません。個人用は地形キャッシュ削除とは別に保持します。
- サーバー側の世界UUIDがない接続では、同じアドレス・同じseed由来値の別ワールドを確実に区別できない既存の限界があります。

初期版には水域の自動抽出、チャンク塗り、MapFrontiers import、Webエリア表示、入退域通知を含めていません。
表示倍率範囲と優先度は保存形式に保持しますが、詳細な倍率・優先度の編集UIは追加候補です。
実装・対象チェック・開発配置の証拠と実機未検証の区別は [F53の台帳](fork-change-register.md) を参照してください。

## English

Available in **26.2 only**, integrated into both loader builds and enabled by default.
Open the fullscreen map and choose **Area drawing**. Choose **Personal (this device)** for local-only data,
or **Shared (server)** to publish areas to supported clients and persist them in the server's world.
Personal areas are never automatically uploaded. Shared editing requires the creator's or an administrator's authority;
assigning a new parent also requires authority over that parent.

Create a name-only feature, a branched line, or a polygon with disconnected parts and holes.
Finish drawing, apply metadata to the draft, then explicitly **Save** the selected storage scope.
Other-scope drafts remain unsaved. The editor supports point editing, independent label positioning, branch separation,
child-region splitting, merging, Undo/Redo, a searchable parent selector, and a persistent Save/Cancel footer.
Use the middle mouse button to pan. Shared saves wait for the server's acknowledgment and keep the draft on failure or conflict.

Area drawing can be disabled without deleting data. Optional minimap names are off by default and appear only on the surface layer.
There is no land protection, automatic water detection, Web area layer, or MapFrontiers import in this initial version.
See the Japanese sections above for storage limits and the [porting register](fork-change-register.md) for verification and deployment evidence.

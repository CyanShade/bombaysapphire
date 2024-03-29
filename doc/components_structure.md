# コンポーネント構成

## 構成図

![コンポーネント構成](components_structure.png)

**Sentinel** は Intel 内の哨戒区域を巡回し情報を収集する。
JavaVM の `SSLSocketFactory` を差し替えることで WebView による Intel 画面の表示を暗黙的に内部の**プロキシサーバ**経由で行わせ、
やり取りされているデータを抽出して **Garuda** に送信する。

GeoCode は緯度/経度から該当する行政区を決定する。

## Sentinel

Ruli ウィルスに感染させ操ることに成功した情報収集用のドローン。Intel 上の哨戒区域を巡回し戦況情報を収集する事を目的とする。

Intel の内部動作をエミュレーションすることは Minify と難読化が施された JavaScript を解析において多大な困難を伴う。
また改変に対して簡単に作業効力を失うことが予想される。このため内部動作をブラックボックスとしそれに対する入出力から
抽出する方法を採る。具体的にはブラウザとIntel との間に抽出用のプロキシを介在させることでこれを行う。

通常の Intel 機能は SSL によって通信内容が暗号化されている。内部プロキシは Intel サイトの設定が施された自己署名証明書を
使用してブラウザと SSL での接続を行う。続いてプロキシはリクエストを受信すると正規の Intel サーバと正規の SSL で接続し
リクエストを転送する。最後に正規の Intel サーバから受信したレスポンスから必要な情報を抽出しブラウザに送信する。
ブラウザはプロキシサーバの自己署名証明書を使用した接続を許可しなければならないだろう。Sentinel は `HTTPSURLConnection`
で全てのサーバ証明書について検証を行わないよう設定されている。

哨戒行動は JavaFX の `WebEngine`/`WebView` を使用することにより WebKit ベースのブラウザと互換性のある動作を行う。
`WebEngine` は表示中のページに対して任意の JavaScript を実行できるためブラウザ表示/入力/遷移をプログラム的に操作することが
可能である。また `WebEngine` は通信に使用する SSL ソケットのファクトリ `SSLSocketFactory` が差し替えられており、
プロキシサーバとの接続時に自己署名証明書を受け入れるため証明書を検証しない `TrustManager` を使用する。またこの
`SSLSocketFactory` は Intel サーバとの接続をローカルのプロキシサーバにすり替える動作を行う。
`WebEngine` としては正規の Intel サーバと SSL で接続しているつもりが、実際には自己署名証明書を使用した
プロキシを中継しているという状況である。

なお `SSLSocketFactory` が生成するソケット上でやりとりされるのは SSL によって暗号化された情報であるため、ファクトリの
差し替えのみでデータを抽出しようとしてもプロキシサーバを介するのとほぼ同じ事を InputStream/OutputStream で行うような
実装となる。

Sentinel は起動時に指定された設定ファイルを読み込み Google アカウントや哨戒行動に必要な情報を参照する。
哨戒位置の移動は文字列としての URL を指定することで該当ページを直接表示させるほか、表示中のページで任意の JavaScript を実行することで
入力やボタン押下を自動化している。

1. 初期画面の表示。画面上には [Sign in] [Cancel] ボタンが表示されている。
1. [Sign in] を押下。Google 認証画面が表示される。
1. JavaScript で設定ファイルのアカウント/パスワードを入力しサインインボタンを押す。
1. 北緯東経 0.00/0.00 が初期画面位置が表示される。
1. 警戒区域内を緯度/経度/ズーム指定で表示する。

Intel で指定位置を表示するには Google Map と同じパラメータが指定できる。

```
https://[サイト名]/intel?ll=[緯度],[経度]&z=17
```

ズームの 17 は Level 0 ポータルが表示されるサイズである (より広域でも表示されていないだけで通信データ上は存在するかもしれないが
保障できないため)。

全世界の地域を巡回することはリソースや巡回時間、アクセス制限の都合上不可能である。
Sentinel は「県」から「町村」に相当する程度の地域にフォーカスを当てて巡回する。
哨戒区域については Sentinel の設定ファイルで以下のいずれかを指定することが出来る。

1. 緯度/経度で指定した矩形。
1. ローカルの KMZ/KML ファイルで定義されている多角形。
1. サーバに定義されていればその行政区。

### セルによる巡回

セルによる巡回は哨戒地区をセル (=Sentinel が1画面で表示する区域) に区切り、北西端から開始し東方向へ、東端に達し
たら南に移動しまた西端から表示を繰り返す。東南端に達すると終了である。システムを駆動させる最初の 1 回目に実行する
必要がある。

セルによる巡回は哨戒区域内の全てを網羅的に収集できる反面、実行に時間がかかる。完全に外れているセルを省略する等の最適化は行われる。

Heuristic Region を指定している場合、対象地域から完全に外れたセルは巡回の対象から外されることでより効率的に動作する。

### Tile Key による巡回

Tile Key は Intel の管理単位である。スコアを算出する Region を構成する縦長の菱形をした領域のようだが正確な定義は不明である。
ポータルやリンクなどの情報は Tile Key 単位で取得できる。

警戒地域全域をセルによる巡回で収集するとポータルが所属する Tile Key も同時に保存される。この Tile Key と Tile Key に
含まれるポータルの位置情報を使用して必要最低限の表示操作だけで警戒地域全域の情報を取得する。
ただし、警戒地域に含むがたまたまポータルが存在しない Tile Key が巡回対象から外されてしまうため、その Tile Key が該当する
地域に新たなポータルが発生した時の発見が遅れることになる。セルによる巡回も定期的に行う必要がある。

### アクセス制限

Intel にもアクセス制限があるようで、東京全域 3km/5秒ごとを 3 ショット行うと Intel からのレスポンスの JSON データがほぼ
全て `{}` になってしまう。これは Intel Sign In 時に使用したアカウントに対する制限のようで、同一 IP アドレスからでも別の
アカウントであれば有効なデータを取得できる。

## ProxyServer

プロキシサーバは BotBrowser と同じプロセス内で動作する。Finagle の HTTP クライアントと HTTP サーバを単純に接続し、
特定の URL に対するレスポンスデータを ParserActor に渡している。現在の Intel の挙動を見る限り `/r/*` を接頭辞に持つ
URL が Intel 表示情報の JSON データを返す API と推測される。この JSON データを抽出するために正規の Intel サーバとの
SSL 通信を自己署名証明書を使用した SSL 通信に置き換えている。このためクライアント側ではこの証明書を使用した SSL 通信を
許可する必要がある。

プロキシサーバは通信の仲介とデータの抽出を行うのみで実際の解析処理は ParserActor によって非同期で行われる。
JSON データは数MBの大きさになることもある。しかし、Akka のリモート構成では一度の通信量が数十kBという制限がある。
設定によりこれを大きくすることもできるが、将来的に通信ログを保存しておき再分析が可能な状態にしておきたいため、
JSON はデータベースに保存してその ID を Akka 経由で渡すように実装している (マシン容量の理由で現在は JSON を受け取ったら
削除している)。

プロキシサーバはローカルホスト上の空いているポートにバインドする。BotBrowser はプロキシサーバのサービス化が開始された
後にそのポート番号へ接続するソケットを構築する。

## ParserActor

プロキシサーバから DB 経由で JSON データを取得し、それぞれのエンティティを解釈して処理を行う Akka Actor。
BotBrowser/プロキシサーバとは別プロセスで起動する。

### JSON データ

* getGameScore … 全世界の Enlightened (以下E)、Resistance (以下R) のスコア。Intel 画面のマップ中央上に表示される情報。
* getRegionScoreDetail … Intel マップが表示しているリージョンの名前、現スコア、チェックポイントごとのスコア、Top 3 エージェントなど。スコアをクリックすると表示される情報。
* getPlext … COMM に表示されるメッセージや通知など。
* getEntities … Intel マップに表示されている Tile Key ごとのポータル、リンク (Edge)、コントロールフィールド (Region) 及び削除済みエンティティの GUID。
* getPortalDetails … ポータルをクリックすると表示される情報。　
* artifact … Helios などのイベントにより特別扱いされているポータルの詳細情報。

上記の JSON データは Intel サーバ側で処理に時間がかかると (任意の要素が?) `result:TIMEOUT` となる。また呼び出し制限に
かかると内容が `{}` となる。

## GeoCode

GeoCode は緯度/経度からその行政区 (国、県、市町村) を決定する。検索に使用する緯度/経度は 10 桁の GeoHash に丸められ、
行政区の決定はその GeoHash 値に対して行われる。GeoHash は 10 桁でおよそ 0.5～1.2m 程度の精度を持つことから、ほぼポータル
個別に割り振られることになる。

検索対象となる位置が Heuristic Region に登録されている既知の行政区に含まれていればその値を使用する。
そうでなければ Google Map API の逆ジオコード API を呼び出して行政区を決定する。
いずれにしても GeoHash 値と結びつけられてデータベースに保存される。

Google Map API のレスポンスには住所を示す複数の結果が含まれている。行政の示す市町村などの他に駅やバス停を示すエントリも
含まれる。このため `address_components` の `types` 値に `political` (行政区) が含まれる情報のみを採用する。`types` には
`country` (国コード), `administrative_area_level_1` (県), `colloquial_area_locality` (郡), `localiti` のみ (区市町村)
の情報も含まれる。

Google Map API 使用制限回避のため新規ポータルの登録に対して行政区の決定は非同期で行われる (このためポータルのレコードは
存在していても行政区情報が未決の状態が存在する)。Google Map API は短時間に大量の呼び出しが行われた場合に一時的な
OVER_QUERY_LIMIT のエラーを返すことがある (この場合 2 秒ほど待てば回復すると公式サイトに書かれている)。この制限の
回避のため、住所の問い合わせはシングルスレッドで最大でも 0.5 秒に 1 回となるように実装している (今のところこれで
OVER_QUERY_LIMIT は出ていない)。これとは別に同一 IP アドレスからの 1 日の API 実行回数が 2,500 回程度に制限されている。
これは 24 時間経過するか別のネットワークに移動すれば利用再開は可能である。

サーバの再起動により行政区の解決がキャンセルされたままのポータルを救助するため 1 時間事に行政区未決のポータルに対して
問い合わせを行うバッチ処理を作成している。

## Heuristic Region

Heuristic Region は既知の行政区領域についての大まかな定義である。システムの高速化と API 呼び出し回数の削減を目的と
している。位置情報から行政区を決定するとき、その地点が Heuristic Region のいずれかに含まれていれば Google Map API
の呼び出しを省略することができる。同様に、警戒地域の巡回地点を決定するするときに対象地点がその Heuristic Region
上の警戒地域を完全に外れている場合は表示を省略することが出来る。

Heuristic Region は kmz 形式で定義した多角形をインポートする。kmz 形式は Google Earth を使用して編集が可能である。

領域の定義には行政区の内側を定義するものと外側を定義するものの 2 種類がある。内側の定義はある地点がこの領域内に
含まれれば確実にその行政区に含まれていることを保障するものであり (十分条件的) 位置情報から行政区を決定するために
使用する。外側の定義はこの領域内を巡回すれば目的の行政区の全てを巡回できることを保障するものであり (必要条件的)
巡回対象の区域かを判断するために使用する。

内側の領域定義は河川や埋め立て地のように境界が曖昧な部分は除外しなければならない。逆に外側の定義ではそれらを含まなけ
ればならない。


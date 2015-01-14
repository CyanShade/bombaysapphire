/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent

import java.io.Closeable
import javafx.application.Platform
import javafx.scene.web.WebEngine

import org.koiroha.bombaysapphire.agent.PatrolSequence.{ByOffset, ByTileKeys}
import org.koiroha.bombaysapphire.{Batch, Context}
import org.slf4j.LoggerFactory
import org.w3c.dom.{Node, NodeList, Element}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Scenario
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * サインインが完了すると地点表示ループに入り北西側の緯度経度から南東側の緯度経度に向かって特定の距離ごとに表示
 * して行く。
 * @author Takami Torao
 */
private class Scenario(conf:Map[String,String], engine:WebEngine, browser:Closeable, proxy:ProxyServer) {
	private[this] val logger = LoggerFactory.getLogger(classOf[Scenario])
	private[this] var signedIn = false

	/** アカウント情報 */
	private[this] val account = conf("account")
	private[this] val password = conf("password")

	/** 次の地点を表示するまでの待機時間 [秒] */
	private[this] val waitInterval = conf.get("interval").map{ _.toLong }.getOrElse(10 * 60L) * 1000L

	/** 開始時刻 */
	private[this] val start = System.currentTimeMillis()

	/** 表示対象地点をあらかじめ算出してキュー化 */
	private[this] val positions = conf.get("explore_method") match {
		case Some(key) =>
			val offset = """offset\(([\d\.]+),\s([\d\.]+)\)""".r
			key match {
				case "existing_tile_key" => new ByTileKeys(proxy)
				case "offset" => ByOffset(Sentinel.areaSize._1, Sentinel.areaSize._2)
				case "offset()" => ByOffset(Sentinel.areaSize._1, Sentinel.areaSize._2)
				case offset(lat,lng) => ByOffset(lat.toDouble, lng.toDouble)
				case unknown =>
					logger.error(s"unsupporeted explore method: $unknown")
					throw new IllegalArgumentException(unknown)
			}
		case None =>
			logger.warn(s"explore_method が設定されていません. 全てのエリアを探索します.")
			ByOffset(Sentinel.areaSize._1, Sentinel.areaSize._2)
	}

	private[this] val max = positions.remains
	logger.info(f"${positions.toString} ごとに $max%,d 地点を探索しています")

	def next():Unit = if(engine.getLocation == s"https://${Context.RemoteHost}/intel") {
		if (! signedIn) {
			// Step1: <a>Sign In</a> をクリックする
			engine.getDocument.getElementsByTagName("a").toList.find { n => n.getTextContent == "Sign in"} match {
				case Some(a: Element) =>
					val url = a.getAttribute("href")
					engine.load(url)
					logger.info(s"初期ページ表示: $url")
				case _ =>
					logger.error(s"初期ページに Sign-in ボタンが見付かりません")
					browser.close()
			}
		} else {
			// 各地点を表示する
			nextMapMove(5000)
		}
	} else if(engine.getLocation.matches("https://accounts\\.google\\.com/ServiceLogin\\?.*")) {
		// サインインを自動化
		engine.executeScript(
			s"""document.getElementById('Email').value='$account';
					|document.getElementById('Passwd').value='$password';
					|document.getElementById('signIn').click();
				""".stripMargin)
		logger.info(s"Sign-in 実行: ${engine.getLocation}")
		signedIn = true
	} else if(engine.getLocation.startsWith(s"https://${Context.RemoteHost}/intel?")){
		None
	} else {
		logger.info(s"予期しないページが表示されました: ${engine.getLocation}")
		browser.close()
	}

	// ============================================================================================
	// 表示位置移動ループ
	// ============================================================================================
	/**
	 * 指定時間後に指定された場所を表示。位置が south/east を超えたらウィンドウを閉じる。
	 */
	def nextMapMove(tm:Long):Unit = {
		if(positions.remains == 0){
			// 全ての地点を表示し終えていたらウィンドウをクローズ
			logger.debug(f"全ての位置が表示されました")
			browser.close()
		} else {
			val (lat, lng) = positions.next
			def _exec() = {
				logger.info(f"[${positions.remains}%,d/$max%,d] 位置を表示しています: ($lat%.6f/$lng%.6f)")
				// 指定された位置を表示; z=17 で L0 のポータルが表示されるズームサイズ
				engine.load(s"https://${Context.RemoteHost}/intel?ll=$lat,$lng&z=17")
				nextMapMove(waitInterval)
			}
			Batch.runAfter(tm){
				Platform.runLater(new Runnable {
					override def run() = _exec()
				})
			}
		}
	}

	implicit class _NodeList(nl:NodeList) {
		def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
	}
}

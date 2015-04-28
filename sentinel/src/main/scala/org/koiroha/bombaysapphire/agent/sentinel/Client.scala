/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.File
import javafx.scene.web.WebView

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.Config
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Client
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * Web ビューとプロキシのセット。
 *
 * @author Takami Torao
 */
class Client(config:Config, val id:Int){
	private[this] val logger = LoggerFactory.getLogger(getClass.getName + ":" + id)

	var overLimit = 0

	val view = new WebView()
	locally {
		view.setZoom(config.scale)
		view.setMinSize(config.viewSize._1, config.viewSize._2)
		view.setPrefSize(config.viewSize._1, config.viewSize._2)
		view.setMaxSize(config.viewSize._1, config.viewSize._2)
		val engine = view.getEngine

		engine.setUserDataDirectory(new File(s"${System.getProperty("user.home", ".")}/.bombaysapphire/sentinel/cache/$id"))
		// ※User-Agent の末尾にIDを付けることで EmbeddedProxy がどのクライアントからのリクエストかを検知する
		//  このIDはIntelへのリクエスト時には削除される
		engine.setUserAgent(s"${config.userAgent} #$id")
		// 初期ページの表示
		engine.load(s"https://${BombaySapphire.RemoteHost}/intel")
	}

	def close():Unit = {
		view.getEngine.load("about:blank")
	}

}

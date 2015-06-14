/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.geometry.{Insets, Orientation}
import javafx.scene.control._
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.text.Font
import javafx.scene.web.WebView

import org.koiroha.bombaysapphire.agent.sentinel.Context

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// SessionTab
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class SessionTab(context:Context) extends Tab {
	val title = new Label()
	val browser = new WebView()
	val status = new Label()

	val account = new ChoiceBox[String]()
	val addAccount = new Button()

	val patrol = new ChoiceBox[String]()

	val exec = new Button()

	locally {
		title.setFont(new Font(title.getFont.getSize * 1.25))
		title.setText("シナリオ")
		title.setPadding(new Insets(4, 6, 4, 6))

		status.setText("ステータス")
		status.setPadding(new Insets(4, 6, 4, 6))

		addAccount.setText("追加")

		patrol.getItems.addAll("マニュアル", "定点", "巡回")

		exec.setText("実行")

		val settings = new HBox()
		settings.getChildren.add(new Label("アカウント"))
		settings.getChildren.add(account)
		settings.getChildren.add(addAccount)
		settings.getChildren.add(new Separator(Orientation.VERTICAL))
		settings.getChildren.add(new Label("哨戒方法"))
		settings.getChildren.add(patrol)
		settings.getChildren.add(new Separator(Orientation.VERTICAL))
		settings.getChildren.add(exec)

		val panel1 = new VBox()
		panel1.getChildren.add(title)
		panel1.getChildren.add(settings)
		panel1.getChildren.add(browser)
		panel1.getChildren.add(status)

		this.setText("Session")
		this.setContent(panel1)

		val config = context.config
		browser.setZoom(config.screenScale)
		browser.setMinSize(config.viewSize._1, config.viewSize._2)
		browser.setPrefSize(config.viewSize._1, config.viewSize._2)
		browser.setMaxSize(config.viewSize._1, config.viewSize._2)
	}
}

/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.asosconsole

import javafx.scene.control.{ToolBar, Button, Label, TextField}
import javafx.scene.layout.{VBox, BorderPane}
import javafx.scene.web.WebView

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Browser
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Browser extends BorderPane {
	private val browser = new WebView()
	private val url = new TextField()
	private val status = new Label()

	locally {
		val reload = new Button("更新")
		val spread = new Button("拡大")
		val capture = new Button("撮影")

		val toolbar = new ToolBar()
		toolbar.getItems.add(reload)
		toolbar.getItems.add(spread)
		toolbar.getItems.add(capture)

		val top = new VBox()
		top.setSpacing(0)
		top.getChildren.addAll(url, toolbar)

		this.setTop(top)
		this.setBottom(status)
		this.setCenter(browser)
	}

	def setPhysicalScale(d:Double):Unit = {
		val bounds = this.getBoundsInParent
		this.setScaleX(d)
		this.setScaleY(d)
		this.setScaleZ(d)
		this.resize(bounds.getWidth / d, bounds.getHeight / d)
	}
}

/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.asosconsole

import javafx.scene.Node
import javafx.scene.layout.{GridPane, Pane}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Layout
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait Layout {
	def name:String
	def layout(nodes:Node*):Pane = {
		val pane = new GridPane()
		pane.setHgap(0)
		pane.setVgap(0)
		layout(pane, nodes:_*)
		pane
	}
	protected def layout(pane:GridPane, nodes:Node*):Unit
}

object Layout {
}

object FullScreenLayout extends Layout {
	val name = "1×1 Full"
	protected def layout(pane:GridPane, nodes:Node*):Unit = {
		pane.add(nodes(0), 0, 0)
	}
}

/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.geometry.Orientation
import javafx.scene.control._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// DashboardTab
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class DashboardTab extends Tab {
	val console = new TextArea()
	var summary = new TableView[String]()



	locally {
		summary.setEditable(false)
		summary.getColumns.addAll(
			new TableColumn[String,String]("Name"),
			new TableColumn[String,String]("Progress")
		)

		console.setEditable(false)
		console.appendText("hello, world\nThis is console message.")

		val pane = new SplitPane()
		pane.setOrientation(Orientation.VERTICAL)
		pane.getItems.addAll(summary, console)

		this.setText("Dashboard")
		this.setClosable(false)
		this.setContent(pane)
	}
}

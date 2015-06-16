/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.application.Platform
import javafx.beans.value.{ObservableValue, ChangeListener}
import javafx.geometry.Insets
import javafx.scene.control._
import javafx.scene.layout.GridPane
import javafx.util.Callback

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AccountDialog
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class AccountDialog extends Dialog[(String,String)] {
	locally {
		setTitle("アカウントの追加")
		setHeaderText("Sentinel の哨戒に使用する Google アカウントのメールアドレスとパスワードを入力して下さい。")
		getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

		val grid = new GridPane()
		grid.setHgap(10)
		grid.setVgap(10)
		grid.setPadding(new Insets(20, 150, 10, 10))

		val username = new TextField()
		username.setPromptText("Username")
		val password = new PasswordField()
		password.setPromptText("Password")

		grid.add(new Label("Username:"), 0, 0)
		grid.add(username, 1, 0)
		grid.add(new Label("Password:"), 0, 1)
		grid.add(password, 1, 1)

		// Enable/Disable login button depending on whether a username was entered.
		val ok = getDialogPane.lookupButton(ButtonType.OK)
		ok.setDisable(true)
		username.textProperty().addListener(new ChangeListener[String] {
			override def changed(observable:ObservableValue[_ <: String], oldValue:String, newValue:String):Unit = {
				ok.setDisable(! newValue.matches("^([a-zA-Z0-9])+([a-zA-Z0-9\\._-])*@([a-zA-Z0-9_-])+([a-zA-Z0-9\\._-]+)+$"))
			}
		})

		getDialogPane.setContent(grid)

		Platform.runLater(new Runnable {
			override def run(): Unit = username.requestFocus()
		})

		setResultConverter(new Callback[ButtonType,(String,String)] {
			override def call(button:ButtonType):(String,String) = button match {
				case ButtonType.OK => (username.getText, password.getText)
				case _ => null
			}
		})
	}
}

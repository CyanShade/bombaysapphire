/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.geometry.Insets
import javafx.scene.control._
import javafx.scene.input.KeyEvent
import javafx.scene.layout.GridPane
import javafx.util.Callback

import org.koiroha.bombaysapphire.agent.sentinel.Context

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AccountDialog
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * アカウントを編集するためのダイアログ。
 *
 * @author Takami Torao
 */
class AccountDialog(context:Context) extends Dialog[String] {

	/**
	 * 入力されたユーザ名の有効性を検証。
	 */
	private[this] def validMailAddress(username:String) = username.matches("^([a-zA-Z0-9])+([a-zA-Z0-9\\._-])*@([a-zA-Z0-9_-])+([a-zA-Z0-9\\._-]+)+$")

	locally {
		setTitle("アカウント管理")
		getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

		// ユーザ名の入力フィールド
		val username = new TextField()
		username.setPromptText("Google Account (mail address)")
		username.setText(context.account.username)

		// パスワードの入力フィールド
		val password = new PasswordField()
		password.setPromptText("Password")
		password.setText(context.account.password)

		// Enable/Disable login button depending on whether a username was entered.
		val ok = getDialogPane.lookupButton(ButtonType.OK)

		// イベントハンドラの設定
		def validateAll():Unit = {
			username.setStyle(if(validMailAddress(username.getText)) "" else "-fx-border-color: red")
			ok.setDisable(! validMailAddress(username.getText) )
		}
		username.setOnKeyTyped({ e:KeyEvent => validateAll() })
		validateAll()

		val grid = new GridPane()
		grid.setHgap(10)
		grid.setVgap(10)
		grid.setPadding(new Insets(20, 150, 10, 10))

		grid.add(new Label("User ID"), 0, 0)
		grid.add(username, 1, 0)
		grid.add(new Label("Password"), 0, 1)
		grid.add(password, 1, 1)

		getDialogPane.setContent(grid)

		setResultConverter(new Callback[ButtonType,String] {
			override def call(button:ButtonType):String = button match {
				case ButtonType.OK =>
					context.account.username = username.getText
					context.account.password = password.getText
					"Ok"
				case _ => null
			}
		})
	}
}

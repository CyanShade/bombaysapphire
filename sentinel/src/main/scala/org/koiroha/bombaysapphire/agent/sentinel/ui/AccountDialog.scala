/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import java.util.Collections
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableListBase
import javafx.event.{ActionEvent, EventHandler}
import javafx.geometry.Insets
import javafx.scene.control.TableColumn.{CellDataFeatures, CellEditEvent}
import javafx.scene.control._
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.layout.GridPane
import javafx.util.Callback
import javafx.util.converter.DefaultStringConverter

import org.koiroha.bombaysapphire.agent.sentinel.Context

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AccountDialog
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * アカウント管理用のダイアログ。
 *
 * @author Takami Torao
 */
class AccountDialog(context:Context) extends Dialog[String] {

	// ユーザ名の有効性を検証
	private[this] def _valid(username:String) = username.matches("^([a-zA-Z0-9])+([a-zA-Z0-9\\._-])*@([a-zA-Z0-9_-])+([a-zA-Z0-9\\._-]+)+$")

	locally {
		setTitle("アカウント管理")
		getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

		case class Account(username:String, password:String){
			def valid = _valid(username)
		}

		// TableView の表示と同期する ObservableList[Account]
		val observable = new ObservableListBase[Account]{
			// アカウント一覧
			var accounts = context.accounts.list.map{ a => Account(a.username, a.password) }
			// アカウントの追加
			override def add(a:Account) = {
				accounts = accounts :+ a
				this.beginChange()
				nextAdd(size()-1, size())
				this.endChange()
				true
			}
			// アカウントの取得
			override def get(index:Int):Account = accounts(index)
			// アカウントの置き換え
			override def set(index:Int, account:Account):Account = {
				val oldValue = accounts(index)
				accounts = (accounts.take(index) :+ account) ++: accounts.drop(index + 1)
				this.beginChange()
				nextReplace(size()-1, size(), Collections.emptyList())
				this.endChange()
				oldValue
			}
			// アカウントの削除
			override def remove(i:Int):Account = {
				val oldValue = accounts(i)
				accounts = accounts.take(i) ++: accounts.drop(i + 1)
				this.beginChange()
				nextRemove(i, oldValue)
				this.endChange()
				oldValue
			}
			// アカウント数の参照
			override def size():Int = accounts.size
			// この内容で context.accounts を更新
			def commit():Unit = {
				context.accounts.list.foreach{ a =>
					accounts.find{ _.username == a.username } match {
						case Some(b) =>
							if(a.password != b.password){
								a.password = b.password
							}
						case None => a.drop()
					}
				}
				accounts.foreach { a =>
					context.accounts.list.find {_.username == a.username} match {
						case Some(_) =>
						case None => context.accounts.create(a.username, a.password)
					}
				}
			}
		}

		val table = new TableView[Account]()
		val username = new TableColumn[Account,String]("User ID")
		val password = new TableColumn[Account,String]("Password")

		// Enable/Disable login button depending on whether a username was entered.
		val ok = getDialogPane.lookupButton(ButtonType.OK)
		ok.setDisable(true)
		def validateAll():Unit = ok.setDisable(observable.accounts.exists{ a => ! _valid(a.username) })

		// Account のプロパティ値をセルの表示値として使用する ValueFactory
		class AccountValueFactory(f:Account=>String) extends Callback[CellDataFeatures[Account,String],ObservableValue[String]] {
			override def call(param:CellDataFeatures[Account, String]):ObservableValue[String] = new SimpleStringProperty(f(param.getValue))
		}

		// セルの編集が終わったタイミングで ObservableList[Account] に値を反映する EventHandler
		class OnEditCommitEventHandler(f:(Account,String)=>Account) extends EventHandler[CellEditEvent[Account,String]] {
			override def handle(event: CellEditEvent[Account, String]): Unit = {
				val account = f(event.getRowValue, event.getNewValue)
				observable.set(event.getTablePosition.getRow, account)
				validateAll()
			}
		}

		// 値がメールアドレス形式でない場合にエラーを表す枠を表示する TableCell
		class AccountTableCell extends Callback[TableColumn[Account,String], TableCell[Account,String]]() {
			override def call(x:TableColumn[Account,String]) = {
				new TextFieldTableCell[Account, String](new DefaultStringConverter()) {
					override def updateItem(item: String, empty: Boolean): Unit = {
						super.updateItem(item, empty)
						if (item == null || empty) {
							setText(null)
							setStyle("")
						} else {
							setText(item)
							if (_valid(item)) {
								setStyle("")
							} else {
								setStyle("-fx-border-color: red")
							}
						}
					}
				}
			}
		}

		username.setCellValueFactory(new AccountValueFactory(_.username))
		username.setCellFactory(new AccountTableCell())
		username.setOnEditCommit(new OnEditCommitEventHandler( (a,u) => a.copy(username=u)))

		password.setCellValueFactory(new AccountValueFactory(_.password))
		password.setCellFactory(TextFieldTableCell.forTableColumn())
		password.setOnEditCommit(new OnEditCommitEventHandler( (a,u) => a.copy(password=u)))

		table.setItems(observable)
		table.getColumns.addAll(username, password)
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY)
		table.setEditable(true)
		table.setTooltip(new Tooltip(s"哨戒に使用するアカウントのメールアドレスとパスワードを入力して下さい。"))

		val add = new Button()
		add.setText("Add")
		add.setOnAction(new EventHandler[ActionEvent] {
			override def handle(event: ActionEvent): Unit = {
				observable.add(Account("unknown", "xyz"))
				validateAll()
			}
		})

		val delete = new Button()
		delete.setText("Remove")
		delete.setOnAction(new EventHandler[ActionEvent] {
			override def handle(event: ActionEvent): Unit = {
				val i = table.getSelectionModel.getSelectedIndex
				if(i >= 0){
					observable.remove(i)
					validateAll()
				}
			}
		})

		val grid = new GridPane()
		grid.setHgap(10)
		grid.setVgap(10)
		grid.setPadding(new Insets(20, 150, 10, 10))

		grid.add(table, 0, 0, 1, 2)
		grid.add(add, 1, 0)
		grid.add(delete, 1, 1)

		getDialogPane.setContent(grid)

		setResultConverter(new Callback[ButtonType,String] {
			override def call(button:ButtonType):String = button match {
				case ButtonType.OK =>
					observable.commit()
					""
				case _ => null
			}
		})
	}
}

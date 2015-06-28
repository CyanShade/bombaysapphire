/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.beans.property._
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.event.Event
import javafx.geometry.{Insets, Pos}
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.control.TableColumn.CellDataFeatures
import javafx.scene.control._
import javafx.scene.control.cell.{CheckBoxTableCell, PropertyValueFactory}
import javafx.scene.layout.{GridPane, HBox}
import javafx.util.Callback

import org.koiroha.bombaysapphire.agent.sentinel._
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// SettingsUI
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class SettingsUI(context:Context, status:StringProperty) extends TabPane {
	import org.koiroha.bombaysapphire.agent.sentinel.ui.SettingsUI._

	val username = new TextField()
	val password = new PasswordField()

	val waypoints = new TextArea()

	val scheduleProperty = new SimpleBooleanProperty()

	object interval extends HBox {
		val from = new Spinner[Integer]()
		val to = new Spinner[Integer]()
		locally {
			from.setValueFactory(new IntegerSpinnerValueFactory(0, 24*60*60, context.scenario.interval.from))
			from.getEditor.textProperty().setModel(context.scenario.interval.from.toString, t => context.scenario.interval.from = t.toInt)
			to.setValueFactory(new IntegerSpinnerValueFactory(0, 24*60*60, context.scenario.interval.to))
			to.getEditor.textProperty().setModel(context.scenario.interval.to.toString, t => context.scenario.interval.to = t.toInt)
			Seq(from, to).foreach{ c =>
				c.getEditor.setPrefColumnCount(5)
				c.setEditable(true)
				c.getEditor.setAlignment(Pos.BASELINE_RIGHT)
				c.getEditor.textProperty().addListener({(o:String,n:String) => statusProperty.update() })
			}
			getChildren.addAll(from, new Label("〜"), to, new Label("秒"))
		}
	}

	object statusProperty extends SimpleStringProperty {
		def update():Unit = {
			val count = context.scenario.waypoints.flatMap{
				case a:Area => a.toWayPoints(context.config.screenRegion)
				case w:WayPoint => Seq(w)
			}.size
			val i = context.scenario.interval.average
			set(f"account workload ${context.account.workload}%,.1f%%\n$count%,d waypoints, about ${count*i}%,.0f[sec]")
		}
	}

	object schedule extends GridPane {
		val minutes = new TextField()
		val hours = new TextField()
		val dates = new TextField()
		val month = new TextField()
		val week = new TextField()
		val enabled = new CheckBox()
		locally {
			this.setHgap(4)
			this.setVgap(4)
			Seq(minutes, hours, dates, month, week).foreach{ _.setPrefColumnCount(2) }
			val ymd = new HBox()
			ymd.getChildren.addAll(month, new Label("月"), dates, new Label("日("), week, new Label("曜日)"))
			this.add(ymd, 0, 0)

			val hm = new HBox()
			hm.getChildren.addAll(hours, new Label("時"), minutes, new Label("分"))
			this.add(hm, 0, 1)

			enabled.setText("自動実行をスケジュール")
			enabled.selectedProperty().addListener({(o:java.lang.Boolean,n:java.lang.Boolean)=>scheduleProperty.set(n)})
			this.add(enabled, 0, 2)

			enabled.selectedProperty().setModel(context.scenario.schedule.enabled, context.scenario.schedule.enabled = _)
			minutes.textProperty().setModel(context.scenario.schedule.minute, context.scenario.schedule.minute = _)
			hours.textProperty().setModel(context.scenario.schedule.hour, context.scenario.schedule.hour = _)
			dates.textProperty().setModel(context.scenario.schedule.date, context.scenario.schedule.date = _)
			month.textProperty().setModel(context.scenario.schedule.month, context.scenario.schedule.month = _)
			week.textProperty().setModel(context.scenario.schedule.weekday, context.scenario.schedule.weekday = _)
		}
		def status():String = {
			if(enabled.isSelected) {
				""
			} else {
				"Periodic batch is not scheduled."
			}
		}
	}

	case class UIDestination(d:Destination){
		private val enabled = new BooleanPropertyBase() {
			override def getName: String = "enabled"
			override def getBean: AnyRef = d
			override def get() = d.enabled
			override def set(value:Boolean) = d.enabled = value
		}
		private val `type` = new ReadOnlyStringPropertyBase() {
			override def getName: String = "type"
			override def getBean: AnyRef = d
			override def get() = d.`type`
		}
		private val text = new ReadOnlyStringPropertyBase() {
			override def getName: String = "text"
			override def getBean: AnyRef = d
			override def get() = d.text
		}
		def enabledProperty = enabled
		def typeProperty = `type`
		def textProperty = text
	}
	object destinations extends TableView[UIDestination] {
		val list = context.destinations.list.map { UIDestination.apply }
		private implicit def _fnc2Callback[U,T](f:(U)=>T):Callback[CellDataFeatures[U,T],ObservableValue[T]] = {
			new Callback[CellDataFeatures[U,T],ObservableValue[T]] {
				override def call(param: CellDataFeatures[U,T]):ObservableValue[T] = {
					new SimpleObjectProperty[T](f(param.getValue))
				}
			}
		}
		val colEnabled = new TableColumn[UIDestination,java.lang.Boolean]()
		val colType = new TableColumn[UIDestination,String]()
		val colUri = new TableColumn[UIDestination,String]()
		colEnabled.setEditable(true)
		colEnabled.setCellValueFactory(new PropertyValueFactory[UIDestination,java.lang.Boolean]("enabled"))
		colEnabled.setCellFactory(CheckBoxTableCell.forTableColumn(colEnabled))
		colEnabled.setText("Use")
		colType.setEditable(false)
		colType.setCellValueFactory(new PropertyValueFactory[UIDestination,String]("type"))
		colType.setText("Type")
		colUri.setEditable(false)
		colUri.setCellValueFactory(new PropertyValueFactory[UIDestination,String]("text"))
		colUri.setText("Destination")
		this.setEditable(true)
		this.getColumns.addAll(colEnabled, colType, colUri)
		this.setItems(FXCollections.observableArrayList(list:_*))
	}

	locally {

		val settings = new GridPane()
		settings.setHgap(4)
		settings.setVgap(4)
		settings.setOpaqueInsets(new Insets(4))

		username.setPromptText("Google Account (email address)")
		username.textProperty().setModel(context.account.username, context.account.username = _)
		settings.add(new Label("User Name"), 0, 0)
		settings.add(username, 0, 1)

		password.setPromptText("Password")
		password.textProperty().setModel(context.account.password, context.account.password = _)
		settings.add(new Label("Password"), 0, 2)
		settings.add(password, 0, 3)

		waypoints.setWrapText(false)
		waypoints.setPromptText("portal:緯度,経度\npoint:緯度,経度\nkeyword:キーワード\nfile:kmlファイル.kml\n:矩形")
		waypoints.setPrefColumnCount(20)
		waypoints.textProperty().setModel(
			context.scenario.waypoints.mkString("\n"),
			t => context.scenario.waypoints = t.split("\n+").filterNot{ _.isEmpty }.flatMap{ ws => WayPoint.parse(ws)})
		waypoints.textProperty().addListener({(o:String,n:String) => statusProperty.update() })

		settings.add(new Label("Waypoints"), 0, 4)
		settings.add(waypoints, 0, 5)

		settings.add(new Label("移動間隔"), 0, 6)
		settings.add(interval, 0, 7)

		val tab1 = new Tab()
		tab1.setText("哨戒")
		tab1.setClosable(false)
		tab1.setContent(settings)

		val batch = new GridPane()
		batch.setHgap(4)
		batch.setVgap(4)

		batch.add(schedule, 0, 0)

		val tab2 = new Tab()
		tab2.setText("自動")
		tab2.setClosable(false)
		tab2.setContent(batch)
		tab2.setOnSelectionChanged({ e:Event =>
			if(tab2.isSelected){
				status.setValue(schedule.status)
			}
		})

		val tab3 = new Tab()
		tab3.setText("出力")
		tab3.setClosable(false)
		tab3.setContent(destinations)

		this.getTabs.addAll(tab1, tab2, tab3)
	}

	/**
	 * 入力内容を確認して実行ボタンの活性/非活性を制御。
	 */
	def validate():Unit = {
	}

	/**
	 * シナリオ実行用に全てのコントロールを非活性化。
	 */
	def setInputDisabled(disabled:Boolean):Unit = fx{
		Seq(
			username, password, waypoints,
			interval.from, interval.to,
			schedule.minutes, schedule.hours, schedule.dates, schedule.month, schedule.week, schedule.enabled
		).foreach{
			case c:TextInputControl => c.setEditable(! disabled)
			case c => c.setDisable(disabled)
		}
	}

}
object SettingsUI {
	private[SettingsUI] val logger = LoggerFactory.getLogger(classOf[ScenarioUI])

	implicit class _StringProperty(t:StringProperty){
		def setModel(get: =>String, set:(String)=>Unit):Unit = {
			t.setValue(get)
			t.addListener({ (oldValue: String, newValue: String) => set(newValue)})
		}
	}
	implicit class _BooleanProperty(t:BooleanProperty){
		def setModel(get: =>Boolean, set:(Boolean)=>Unit):Unit = {
			t.setValue(get)
			t.addListener({ (oldValue:java.lang.Boolean, newValue:java.lang.Boolean) => set(newValue)})
		}
	}
	implicit class _ReadOnlyObjectProperty[T](t:ReadOnlyObjectProperty[T]){
		def setModel(set:(T)=>Unit):Unit = {
			t.addListener({ (oldValue:T, newValue:T) => set(newValue)})
		}
	}

}
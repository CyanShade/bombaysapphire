/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.beans.value.{ObservableValue, ChangeListener}
import javafx.event.{Event, ActionEvent, EventHandler}
import javafx.geometry.Insets
import javafx.scene.control._
import javafx.scene.layout.{BorderPane, GridPane, HBox}
import javafx.scene.web.WebView

import org.koiroha.bombaysapphire.agent.sentinel.Scenario.{Interval, Schedule}
import org.koiroha.bombaysapphire.agent.sentinel._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ScenarioTab
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ScenarioTab(context:Context, scenario:Scenario) extends Tab {
	private[this] val logger = LoggerFactory.getLogger(classOf[ScenarioTab])
	val browser = new WebView()
	val status = new Label()

	val account = new ChoiceBox[String]()
	val addAccount = new Button()

	val patrol = new TextArea()
	val url = new TextField()

	object interval extends HBox {
		val from = new TextField()
		val to = new TextField()
		locally {
			from.setText("10")
			to.setText("10")
			Seq(from, to).foreach{ _.setPrefColumnCount(2) }
			getChildren.addAll(from, new Label("〜"), to, new Label("秒"))
		}
		def toInterval:Interval = Interval(from.getText.toInt, to.getText.toInt)
		def fromInterval(i:Interval):Unit = {
			from.setText(i.from.toString)
			to.setText(i.to.toString)
		}
	}

	object schedule extends HBox {
		val minutes = new TextField("0")
		val hours = new TextField("0")
		val dates = new TextField("*")
		val month = new TextField("*")
		val week = new TextField("*")
		val exec = new Button("起動")
		locally {
			Seq(minutes, hours, dates, month, week).foreach{ _.setPrefColumnCount(2) }
			getChildren.addAll(
				month, new Label("月"), dates, new Label("日("), week, new Label("曜日)"), hours, new Label("時"), minutes, new Label("分"))
		}
		def toSchedule:Schedule = Scenario.Schedule(minutes.getText, hours.getText, dates.getText, month.getText, week.getText)
		def fromSchedule(s:Schedule):Unit = {
			minutes.setText(s.minute)
			hours.setText(s.hour)
			dates.setText(s.date)
			month.setText(s.month)
			week.setText(s.weekday)
		}
	}

	val exec = new Button()

	var _session:Option[Session] = None

	locally {
		status.setText("ステータス")
		status.setPadding(new Insets(4, 6, 4, 6))

		// アカウント選択用ドロップダウンリスト
		account.getItems.addAll(context.accounts.list.map{ _.username }:_*)

		addAccount.setText("追加")

		patrol.setWrapText(false)
		patrol.setPromptText("portal:緯度,経度\npoint:緯度,経度\nkeyword:キーワード\nfile:kmlファイル.kml\n:矩形")
		patrol.setPrefColumnCount(20)

		exec.setText("実行")
		exec.setOnAction(new EventHandler[ActionEvent] {
			override def handle(event: ActionEvent):Unit = run()
		})

		val settings = new GridPane()
		settings.setHgap(4)
		settings.setVgap(4)
		settings.setOpaqueInsets(new Insets(4))
		settings.add(new Label("アカウント"), 0, 0, 2, 1)
		settings.add(account, 0, 1)
		settings.add(addAccount, 1, 1)
		settings.add(new Label("哨戒対象"), 0, 2, 2, 1)
		settings.add(patrol, 0, 3, 2, 1)
		settings.add(new Label("移動間隔"), 0, 4, 2, 1)
		settings.add(interval, 0, 5, 2, 1)
		settings.add(new Label("スケジュール"), 0, 6, 2, 1)
		settings.add(schedule, 0, 7, 2, 1)
		settings.add(exec, 1, 10)

		val main = new BorderPane()
		main.setTop(url)
		main.setCenter(browser)

		val panel1 = new BorderPane()
		panel1.setRight(settings)
		panel1.setCenter(main)
		panel1.setBottom(status)

		this.setText("Session")
		this.setContent(panel1)

		url.setEditable(false)

		val config = context.config
		// browser.setZoom(0.6)
		browser.setMinSize(config.viewSize._1, config.viewSize._2)
		browser.setPrefSize(config.viewSize._1, config.viewSize._2)
		browser.setMaxSize(config.viewSize._1, config.viewSize._2)
		browser.getEngine.load(context.config.defaultUrl)
		browser.getEngine.locationProperty().addListener(new ChangeListener[String] {
			override def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
				url.setText(newValue)
			}
		})

		addAccount.onActionProperty().setValue(new EventHandler[ActionEvent] {
			override def handle(event: ActionEvent): Unit = {
				val dialog = new AccountDialog(context)
				val result = dialog.showAndWait()
				if(result.isPresent){
					context.save()
				}
			}
		})

		this.setOnCloseRequest(new EventHandler[Event] {
			override def handle(event: Event): Unit = {
				updateContext()
				scenario.hidden = true
				context.save()
			}
		})
	}

	/**
	 * Context の内容をこのタブに反映させます。
	 */
	def updateView():Unit = {
		context.accounts.list.find{ _.username == scenario.account}.map{ _.username }.foreach{ account.setValue }
		patrol.setText(scenario.waypoints.map{ _.toString }.mkString("\n"))
		interval.fromInterval(scenario.interval)
		schedule.fromSchedule(scenario.schedule)
	}

	/**
	 * このタブに表示されている入力内容を Context に反映させます。
	 */
	def updateContext():Unit = {
		scenario.account = Option(account.getValue).getOrElse("")
		scenario.waypoints = patrol.getText.split("\n+").filterNot{ _.isEmpty }.flatMap{ ws => WayPoint.parse(ws)}
		scenario.interval = interval.toInterval
		scenario.schedule = schedule.toSchedule
	}

	/**
	 * 入力内容を確認して実行ボタンの活性/非活性を制御。
	 */
	def validate():Unit = {
		if(account.getSelectionModel.getSelectedIndex < 0){
			account.setStyle("border-color:red")
		}
	}

	/**
	 * シナリオ実行用に全てのコントロールを非活性化。
	 */
	def setInputDisabled(disabled:Boolean):Unit = fx{
		Seq(
			account, addAccount, patrol,
			interval.from, interval.to,
			schedule.minutes, schedule.hours, schedule.dates, schedule.month, schedule.week, schedule.exec
		).foreach{
			case c:TextInputControl => c.setEditable(! disabled)
			case c => c.setDisable(disabled)
		}
		if(disabled) {
			browser.getEngine.load(context.config.defaultUrl)
			exec.setText("中止")
		} else {
			exec.setText("実行")
		}
	}

	/**
	 * シナリオの実行。
	 */
	def run():Unit = _session match {
		case Some(s) =>
			s.stop()
			_session = None
		case None =>
			import scala.concurrent.ExecutionContext.Implicits.global
			setInputDisabled(true)
			updateContext()
			context.save()
			_session = Some(new Session(context, scenario, browser))
			_session.foreach{ _.start().onComplete{
				case Success(_) =>
					setInputDisabled(false)
				case Failure(ex) =>
					setInputDisabled(false)
			} }
	}

	updateView()
}

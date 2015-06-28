/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel.ui

import javafx.concurrent.Worker
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.scene.control._
import javafx.scene.input.{KeyCombination, KeyCode, KeyCodeCombination}
import javafx.scene.layout._
import javafx.scene.web.WebView
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.{FileChooser, Stage}

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.sentinel._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}
import scala.xml.XML

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ScenarioTab
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ScenarioUI(context:Context, stage:Stage) extends Pane {
	private[this] val logger = LoggerFactory.getLogger(classOf[ScenarioUI])
	val browser = new WebView()
	val status = new Label()

	val settings = new SettingsUI(context, status.textProperty())
	val url = new TextField()

	val indicator = new ProgressIndicator()
	val progress = new Label()

	val desc = new Label()

	/**
	 * 開始/中止ボタン。
	 */
	val exec = new Button()

	var _session:Option[Session] = None

	locally {
		val config = context.config

		// メニューバー
		val menuBar = new MenuBar()
		locally {
			// File メニュー
			val file = new Menu("File")
			val save = new MenuItem("Save")
			val quit = new MenuItem("Quit")
			save.setOnAction({ e:ActionEvent => context.save() })
			save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN))
			quit.setOnAction({ e:ActionEvent => stage.close() })
			file.getItems.addAll(save, new SeparatorMenuItem(), quit)
			// Debug メニュー
			val debug = new Menu("Debug")
			val exportKML = new MenuItem("Export Waypoints KML")
			exportKML.setOnAction({ e:ActionEvent => exportWayPointKML() })
			debug.getItems.addAll(exportKML)

			menuBar.getMenus.addAll(file, debug)
		}

		status.setText("ステータス")
		status.setPadding(new Insets(4, 6, 4, 6))

		url.setEditable(false)

		settings.statusProperty.addListener({(o:String,n:String) => desc.setText(n) })
		settings.scheduleProperty.addListener({(o:java.lang.Boolean,n:java.lang.Boolean) => schedule(n) })

		indicator.setDisable(true)
		indicator.setProgress(0)
		progress.setMaxWidth(180)

		exec.setText("実行")
		exec.setOnAction({ e:ActionEvent => execute() })

		val tools = new ToolBar()
		tools.getItems.addAll(exec, desc, indicator, progress)

		val top = new VBox()
		top.getChildren.addAll(menuBar, tools, url)

		val wrapper = new AnchorPane()
		wrapper.getChildren.add(browser)
		wrapper.setMinSize(config.physicalScreen.width, config.physicalScreen.height)
		wrapper.setPrefSize(config.physicalScreen.width, config.physicalScreen.height)
		wrapper.setMaxSize(config.physicalScreen.width, config.physicalScreen.height)

		val center = new GridPane()
		center.setHgap(4)
		center.add(wrapper, 0, 0)
		center.add(settings, 1, 0)

		val main = new BorderPane()
		main.setTop(top)
		main.setCenter(center)
		main.setBottom(status)

		this.getChildren.add(main)

		browser.setMinSize(config.physicalScreen.width, config.physicalScreen.height)
		browser.setPrefSize(config.physicalScreen.width, config.physicalScreen.height)
		browser.setMaxSize(config.physicalScreen.width, config.physicalScreen.height)
		browser.getEngine.load(context.config.defaultUrl)
		browser.getEngine.locationProperty().addListener({ (oldValue:String, newValue:String) =>
			url.setText(newValue)
		})
		browser.getEngine.getLoadWorker.stateProperty().addListener({ (oldValue:Worker.State, newValue:Worker.State) =>
			status.setText(s"[$newValue] ${browser.getEngine.getLocation}")
			if(newValue == Worker.State.FAILED){
				execute()   // 停止
			}
		})
	}

	/**
	 * 入力内容を確認して実行ボタンの活性/非活性を制御。
	 */
	def validate():Unit = {
		exec.setDisable(false)
	}

	/**
	 * シナリオ実行用に全てのコントロールを非活性化。
	 */
	def setInputDisabled(disabled:Boolean):Unit = fx{
		settings.setInputDisabled(disabled)
		if(disabled) {
			browser.getEngine.load(context.config.defaultUrl)
			exec.setText("中止")
			indicator.setDisable(false)
		} else {
			indicator.setDisable(true)
			exec.setText("実行")
		}
	}

	/**
	 * シナリオの実行。
	 */
	private[this] def execute():Unit = _session match {
		case Some(s) =>
			logger.info(s"シナリオを停止します")
			s.stop()
			_session = None
		case None =>
			logger.info(s"シナリオを開始します")
			import org.koiroha.bombaysapphire.agent.sentinel.ui._
			import scala.concurrent.ExecutionContext.Implicits.global
			setInputDisabled(true)
			context.save()
			_session = Some(new Session(context, browser))
			_session.foreach{ s =>
				s.progressProperty.addListener({(oldValue:Number,newValue:Number) => fx{
					indicator.setProgress(newValue.doubleValue())
				} })
				s.terminationProperty.addListener({(oldValue:Number,newValue:Number) => fx{
					val tip = if(newValue.doubleValue() < 0.01) {
						""
					} else {
						val term = newValue.longValue()
						val left = term - System.currentTimeMillis()
						f"残り約${left/60/1000}%,d分 ($term%tp$term%tl:$term%tM頃)"
					}
					progress.setText(tip)
				}})
				s.start().onComplete{
				case Success(_) =>
					setInputDisabled(false)
				case Failure(ex) =>
					setInputDisabled(false)
			} }
	}

	private[this] var batch:Option[String] = None
	private[this] def schedule(enabled:Boolean):Unit = if(enabled) {
		batch.foreach{ context.scenario.schedule.deschedule }
		batch = Some(context.scenario.schedule.schedule {
			fx {
				if(_session.isEmpty){
					execute()
				}
			}
		})
		logger.info(s"スケジュール実行が登録されました: ${batch.get}")
	} else {
		batch.foreach{ context.scenario.schedule.deschedule }
		batch = None
		logger.info(f"スケジュール実行がキャンセルされました")
	}

	private[this] def exportWayPointKML():Unit = {
		val fileChooser = new FileChooser()
		fileChooser.setTitle("Open Resource File")
		fileChooser.getExtensionFilters.addAll(
			new ExtensionFilter("KML Files", "*.kml", "*.kmz"))
		val selectedFile = fileChooser.showSaveDialog(stage)
		if (selectedFile != null) {
			val unit = context.config.screenRegion
			val points = context.scenario.allWaypoints(unit).collect{
				case Portal(lat,lng) => (lat,lng,false)
				case FixedPoint(lat,lng) => (lat,lng,true)
			}
			val kml = <kml>
				<Folder>
					<StyleMap id="view-area">
						<Pair>
							<key>normal</key>
							<styleUrl>#view-area-normal</styleUrl>
						</Pair>
						<Pair>
							<key>highlight</key>
							<styleUrl>#view-area-hilight</styleUrl>
						</Pair>
					</StyleMap>
					<Style id="view-area-normal">
						<LineStyle>
							<color>80ffffff</color>
						</LineStyle>
						<PolyStyle>
							<color>FFffffff</color>
						</PolyStyle>
					</Style>
					<Style id="view-area-hilight">
						<IconStyle>
							<scale>1.2</scale>
						</IconStyle>
						<LineStyle>
							<color>8000ffff</color>
						</LineStyle>
						<PolyStyle>
							<color>8080ffff</color>
						</PolyStyle>
					</Style>
					{ points.filter{ _._3 }.map { case (lat, lng, _) =>
					<Placemark>
						<name>"Fixed Point Area"</name>
						<styleUrl>#view-area</styleUrl>
						<Polygon>
							<outerBoundaryIs>
								<LinearRing>
									<coordinates>
										{lng - BombaySapphire.lngUnit(lat) * unit.width / 2},{lat - BombaySapphire.latUnit * unit.height / 2},0
										{lng - BombaySapphire.lngUnit(lat) * unit.width / 2},{lat + BombaySapphire.latUnit * unit.height / 2},0
										{lng + BombaySapphire.lngUnit(lat) * unit.width / 2},{lat + BombaySapphire.latUnit * unit.height / 2},0
										{lng + BombaySapphire.lngUnit(lat) * unit.width / 2},{lat - BombaySapphire.latUnit * unit.height / 2},0
									</coordinates>
								</LinearRing>
							</outerBoundaryIs>
						</Polygon>
					</Placemark> }}
					{ points.map { case (lat, lng, wide) =>
					<Placemark>
						<name>{if(wide) "Fixed Point" else "Portal"}</name>
						<Point>
							<coordinates>{lng},{lat},0</coordinates>
						</Point>
					</Placemark> }}
				</Folder>
			</kml>
			XML.save(selectedFile.toString, kml, enc="UTF-8", xmlDecl=true)
		}
	}

}

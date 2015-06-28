/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javafx.stage.Screen
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import org.koiroha.bombaysapphire.BombaySapphire
import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.koiroha.bombaysapphire.geom.Dimension
import org.slf4j.LoggerFactory
import org.w3c.dom.{Document, Element}
import org.xml.sax.InputSource

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @param file Sentinel の設定/作業ディレクトリ
 * @author Takami Torao
 */
class Context(val file:File) {
	import org.koiroha.bombaysapphire.agent.sentinel.Context._

	/**
	 * 設定ファイルの内容。
	 */
	private[this] val context:Document = locally {
		val binary = if(file.isFile){
			logger.debug(s"loading context from local xml file: $file")
			Files.readAllBytes(file.toPath)
		} else {
			logger.debug(s"local xml file is not exist, apply default configuration: $file")
			s"""<?xml version="1.0" encoding="UTF-8"?>
			|<sentinel>
			|<config>
			|<param name="default-url" value="http://${BombaySapphire.RemoteHost}/events"/>
			|<param name="user-agent" value="Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36 (Parasitized)"/>
			|</config>
			|<account/>
			|<scenario interval="60,60" schedule="0 0 * * *"><waypoints/></scenario>
			|<destinations>
			|<sink uri="garuda:http://localhost:8099/api/1.0" enabled="true"/>
			|<sink uri="${new File(file.getParentFile, "sentinel.log").getCanonicalFile.toURI}" enabled="false"/>
			|</destinations>
			|</sentinel>""".stripMargin('|').getBytes(StandardCharsets.UTF_8)
		}
		val src = new InputSource(new ByteArrayInputStream(binary))
		src.setSystemId(file.toURI.toString)
		DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(src)
	}
	assert(context.getDocumentElement.getTagName == "sentinel")

	// ==============================================================================================
	// 設定
	// ==============================================================================================
	/**
	 * Key-Value 型の設定
	 */
	object config{
		private[this] val logger = LoggerFactory.getLogger(getClass)
		private[this] val root = context.getDocumentElement \+ "config"
		private[this] def param(name:String):Option[Element] = (root \* "param").find{ _.attr("name") == name }
		private[this] def get(name:String, default:String = ""):String = param(name) match {
			case Some(elem) => if(elem.hasAttr("value")) elem.attr("value") else default
			case None => default
		}
		private[this] def get(name:String, default:Int):Int = try {
			get(name, default.toString).toInt
		} catch {
			case ex:NumberFormatException =>
				logger.warn(s"not a number: $name = ${get(name)}")
				default
		}
		private[this] def get(name:String, default:Double):Double = try {
			get(name, default.toString).toDouble
		} catch {
			case ex:NumberFormatException =>
				logger.warn(s"not a number: $name = ${get(name)}")
				default
		}
		private[this] def set(name:String, value:String):Unit = param(name) match {
			case Some(elem) => elem.attr(name, value)
			case None =>
				val elem = root.getOwnerDocument.createElement("param")
				elem.attr("name", name)
				elem.attr("value", value)
				root.appendChild(elem)
		}
		private[this] def set(name:String, value:Int):Unit = set(name, value.toString)
		private[this] def set(name:String, value:Double):Unit = set(name, value.toString)
		/** アイドル状態の時に表示するデフォルトページ */
		def defaultUrl = get("default-url", s"http://${BombaySapphire.RemoteHost}/intel")
		def defaultUrl_=(url:String) = set("default-url", url)
		/** リクエストに使用する User-Agent 名。 */
		def userAgent = get("user-agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36 (Parasitized)")
		def userAgent_=(ua:String) = set("user-agent", ua)
		/** シナリオを中止する OverLimit 発生回数。 */
		def overLimitCountToStopScenario:Int = get("over-limit", 10)
		def overLimitCountToStopScenario_=(count:Int) = set("over-limit", count)
		/** 領域巡回時の移動距離 [km] */
		def distanceKM:Double = get("distance", 1.0)
		def distanceKM_=(d:Double) = set("distance", d)

		/** Sentinel で表示する Intel Map の論理スクリーンサイズ */
		// val logicalScreenSize = (5120, 2880)
		val logicalScreenSize = {
			val s = Screen.getPrimary.getVisualBounds
			Dimension(s.getWidth, s.getHeight)
		}
		/** 物理スクリーン幅 */
		private[this] val physicalScreenWidth = 800
		/** スクリーンの縮小率 */
		val screenScale = physicalScreenWidth.toDouble / logicalScreenSize.width
		/** Intel Map 表示領域の物理スクリーンサイズ (800x450) */
		val physicalScreen = Dimension(logicalScreenSize.width * screenScale, logicalScreenSize.height * screenScale)
		/** z=17 において 100[m]=206[pixel] (Retina), 1kmあたりのピクセル数 */
//		private[this] val unitKmPixels = 206 * (1000.0 / 100)
		/** z=17 において 1.67[km]=1740[pixel] (Retina), 1kmあたりのピクセル数 */
		private[this] val unitKmPixels = 1740 / 1.67
		/** 表示領域が示す縦横の距離[km] (実際はマージンがあるが) */
		val screenRegion = Dimension(logicalScreenSize.width / unitKmPixels, logicalScreenSize.height / unitKmPixels)
		logger.info(f"スクリーンの実際の距離: ${screenRegion.width}%.2fkm × ${screenRegion.height}%.2fkm")
	}

	// ==============================================================================================
	// アカウント
	// ==============================================================================================
	/**
	 * Sentinel のシナリオ実行に使用するアカウント。
	 */
	val account = new Account(context.getDocumentElement \+ "account")

	// ==============================================================================================
	// シナリオ
	// ==============================================================================================
	/**
	 * 実行可能なシナリオ。
	 */
	val scenario = new Scenario(context.getDocumentElement \+ "scenario")

	// ==============================================================================================
	// 宛先
	// ==============================================================================================
	/**
	 * Sentinel が取得したデータを保存/送信するストレージや Web API など。
	 */
	object destinations {
		private[this] val root = context.getDocumentElement \+ "destinations"
		/** 転送先一覧 */
		def list = root.*.map {Destination.parse}
		def create(uri: String): Destination = {
			val elem = Destination.create(root.getOwnerDocument, uri)
			root << elem
			Destination.parse(elem)
		}
		/** リクエスト/レスポンスの保存 */
		def store(method:String, request:String, response:String):Unit = {
			val now = System.currentTimeMillis()
			list.filter{ _.enabled }.foreach{ _.store(method, request, response, now) }
		}
	}

	// ==============================================================================================
	// 新規セッションIDの参照
	// ==============================================================================================
	/**
	 * 新しいセッションのためのIDを参照します。
	 */
	def newSessionId:Int = sessionSeed.getAndIncrement
	private[this] val sessionSeed = new AtomicInteger(0)

	// ==============================================================================================
	// 設定の保存
	// ==============================================================================================
	/**
	 * この内容をファイルに保存します。
	 */
	def save():Unit = {
		val dir = file.getParentFile
		if(! dir.isDirectory){
			logger.info(s"context directory is not exist, create new one: $dir")
			dir.mkdirs()
		}
		context.prettify()
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(context), new StreamResult(file))
		logger.debug(s"context saved")
	}

	/*
	/** 哨戒領域 */
	lazy val patrolRegion:Region = getNameValues("patrol.region") match {
		case Some(("rect", area)) =>
			area match {
				case Seq(name, north, east, south, west) =>
					Region(name, Seq(Rectangle(north.toDouble, east.toDouble, south.toDouble, west.toDouble)))
				case unexpected =>
					logger.error(s"'rect' のパラメータが不正です. (name,north,east,south,west) を指定してください")
					throw new IllegalArgumentException(s"'rect' should be (north,east,south,west): (${unexpected.mkString(",")})")
			}
		case Some(("server", district)) =>
			val f = district match {
				case Seq(country, state, city) => garuda.administrativeDistrict(country, state, city)
				case Seq(country, state) => garuda.administrativeDistrict(country, state, "")
				case Seq(country) => garuda.administrativeDistrict(country, "", "")
			}
			Await.result(f, Duration.Inf) match {
				case Some(reg) => reg
				case None =>
					logger.error(s"指定された行政区 ${district.mkString(",")} はサーバに定義されていません")
					throw new IllegalStateException(s"specified administrative district is not defined")
			}
		case Some(("local", district)) =>
			val Seq(file, country, state, city) = (district ++ Seq("", "", "")).take(4)
			KML.fromFile(new File(file).getCanonicalFile).find{ ad =>
				(country == "") || (ad.country == country && ad.state == state && ad.city == city)
			} match {
				case Some(reg) => reg.toRegion
				case None =>
					logger.error(s"指定された行政区はKMZファイル内に定義されていません: $country, $state, $city")
					throw new IllegalStateException(s"specified administrative district is not defined")
			}
		case Some((unexpected, _)) =>
			logger.error(s"巡回領域の指定が不正です: $unexpected")
			throw new IllegalStateException()
		case None =>
			logger.error(s"巡回領域が定義されていません")
			throw new IllegalStateException()
	}

	/** 巡回区域 */
	def newWayPoints(tileKeysIgnoreable:(String)=>Boolean):WayPoints = getNameValues("patrol.algorithm") match {
		case Some(("tile_keys", Seq())) => new ByTileKeys(garuda, tileKeysIgnoreable)
		case Some(("all", Seq())) => new ByOffset(areaSize._1, areaSize._2)
		case Some(("offset", Seq(latkm,lngkm))) => new ByOffset(latkm.toDouble, lngkm.toDouble)
		case Some((name, _)) =>
			logger.error(s"哨戒方法を認識できません: $name")
			throw new IllegalArgumentException(name)
		case None =>
			logger.warn(s"patrol.algorithm が設定されていません. 全てのエリアを探索します.")
			new ByOffset(areaSize._1, areaSize._2)
	}

	/** Intel アクセス用のアカウント名 */
	lazy val account = config("account")
	/** Intel アクセス用アカウントのパスワード */
	lazy val password = config("password")
	/** 表示間隔[秒] */
	lazy val intervalSeconds = config.get("interval").map{ _.toLong }.getOrElse(60 * 1000L)
	/** User-Agent */
	lazy val userAgent = config.getOrElse("user-agent",
		"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36")
	// Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.44 (KHTML, like Gecko) JavaFX/8.0 Safari/537.44
	// Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36
	lazy val overLimit = config.get("overlimit.ignore").map{ _.toInt }.getOrElse(5)

	private[this] val NamePattern = """([a-zA-Z0-9\._]+)""".r
	private[this] val NameValuesPattern = """([a-zA-Z0-9\._]+)\s*\(\s*(.*)\s*\)""".r
	private[this] def getNameValues(key:String):Option[(String,Seq[String])] = config.get(key).map{ value =>
		value.trim() match {
			case NamePattern(name) => (name.trim, Seq())
			case NameValuesPattern(name, args) =>
				(name, args.split("\\s*,\\s*").map{ _.trim }.toSeq)
		}
	}
	*/

}

object Context {
	private[Context] val logger = LoggerFactory.getLogger(classOf[Context])

}

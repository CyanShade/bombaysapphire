/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import javafx.application.Platform
import javafx.beans.value.{ObservableValue, ChangeListener}
import javafx.event.{Event, EventHandler}
import javafx.scene.transform.Scale
import javafx.scene.web.WebView

import org.koiroha.bombaysapphire.geom.Dimension
import org.slf4j.LoggerFactory

import scala.concurrent.{Promise, Future}
import scala.language.implicitConversions

package object ui {
	private val logger = LoggerFactory.getLogger(this.getClass.getName.dropRight(1))

	def fx[U](f: =>U):Future[U] = if(Platform.isFxApplicationThread){
		Future.successful(f)
	} else {
		val promise = Promise[U]()
		Platform.runLater(new Runnable {
			override def run() = promise.success(f)
		})
		promise.future
	}

	implicit def _func2EventHandler[T<:Event,U](f:(T)=>U):EventHandler[T] = new EventHandler[T] {
		override def handle(event:T):Unit = f(event)
	}
	implicit def _func2ChangeListener[T,U](f:(T,T)=>U):ChangeListener[T] = new ChangeListener[T] {
		override def changed(observable: ObservableValue[_ <: T], oldValue: T, newValue: T): Unit = {
			f(oldValue, newValue)
		}
	}

	implicit class _WebView(browser:WebView){

		// ==============================================================================================
		// ブラウザズームの設定
		// ==============================================================================================
		/**
		 * ブラウザのズームを設定します。
		 */
		def physicalZoom(d:Double, size:Dimension):Unit = {
			logger.debug(f"set browser zoom level: ${d*100}%.1f%%")
			browser.getTransforms.clear()
			browser.getTransforms.add(new Scale(d, d))
			browser.setMaxSize(size.width / d, size.height / d)
			browser.setMinSize(size.width / d, size.height / d)
			browser.setPrefSize(size.width / d, size.height / d)
		}

	}

}

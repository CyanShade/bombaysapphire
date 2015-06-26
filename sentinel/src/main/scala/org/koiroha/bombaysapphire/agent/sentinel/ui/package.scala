/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import javafx.application.Platform
import javafx.event.{Event, EventHandler}

import scala.concurrent.{Promise, Future}

package object ui {

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

}

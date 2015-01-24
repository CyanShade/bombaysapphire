/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.util.{TimerTask, Timer}

import org.slf4j.LoggerFactory

import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Batch
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Batch {
	private[this] val logger = LoggerFactory.getLogger(getClass)
	private[this] val timer = new Timer("BombaySapphire", true)

	/**
	 * 指定時間後に処理を実行する。
	 */
	def runAfter[T](delay:Long)(exec: =>T):Future[T] = {
		val promise = Promise[T]()
		timer.schedule(new TimerTask {
			override def run(): Unit = Try(exec) match {
				case Success(r) =>
					promise.success(r)
				case Failure(ex) =>
					logger.error(s"fail to execute batch", ex)
					promise.failure(ex)
			}
		}, delay)
		promise.future
	}

	/**
	 * 指定時間後に処理を実行する。
	 */
	def runEveryAfter[T](delay:Long)(exec: =>T):Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = Try(exec) match {
				case Success(_) =>
				case Failure(ex) =>
					logger.error(s"fail to execute batch", ex)
			}
		}, delay, delay)
	}

}

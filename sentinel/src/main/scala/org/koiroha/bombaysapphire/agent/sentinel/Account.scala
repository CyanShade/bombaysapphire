/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.util.concurrent.atomic.AtomicReference

import org.koiroha.bombaysapphire.agent.sentinel.xml._
import org.w3c.dom.Element

import scala.annotation.tailrec

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Account
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Account(elem:Element){

	def username:String = elem.attr("username").trim
	def username_=(value:String) = elem.attr("username", value)

	def password:String = elem.attr("password").trim
	def password_=(value:String) = elem.attr("password", value)

	private[this] val _used = new AtomicReference(elem.attr("used-at").split("\\s*,\\s*").map{ _.trim() }.filterNot{ _.isEmpty }.map{ _.toLong }.toList)

	// ==============================================================================================
	/**
	 * このアカウントの UsedLimitTerm 期間内のリクエスト回数を返します。
	 */
	def used:Int = {
		truncate()
		_used.get.size
	}

	// ==============================================================================================
	/**
	 * このアカウントが行ったリクエストが UsedLimitTerm 期間内の上限を超えているかを判定します。
	 */
	def overLimit:Boolean = used >= Account.UsedLimitCount

	// ==============================================================================================
	/**
	 * このアカウントのリクエスト数上限に対する負荷を参照します。
	 */
	def workload:Double = used / Account.UsedLimitCount.toDouble

	// ==============================================================================================
	/**
	 * このアカウントが行ったリクエスト数を加算します。
	 */
	def increment():Unit = {
		@tailrec
		def rec():Unit = {
			val old = _used.get()
			val recent = old :+ System.currentTimeMillis()
			if(! _used.compareAndSet(old, recent)){
				rec()
			} else {
				(elem \+ "request-timestamp").text = recent.mkString(",")
			}
		}
		truncate()
		rec()
	}

	// ==============================================================================================
	/**
	 * 使用時刻を現在時刻から UsedLimitTerm より後のもののみに設定。
	 */
	private[this] def truncate():Unit = {
		@tailrec
		def rec():Unit = {
			val now = System.currentTimeMillis()
			val old = _used.get()
			if(! _used.compareAndSet(old, old.filter{ _ >= now - Account.UsedLimitTerm })){
				rec()
			}
		}
		rec()
	}

	override def toString = username
}

object Account {
	/** 使用回数の制限。 */
	val UsedLimitCount = 100
	/** 使用回数制限の期間 */
	val UsedLimitTerm = 1 * 24 * 60 * 60 * 1000L

}
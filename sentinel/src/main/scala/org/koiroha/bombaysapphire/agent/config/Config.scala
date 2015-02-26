/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.config

import scala.xml.Elem

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Config
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
case class Config() {
}

case class Account(name:String, password:String, var achievements:Account.Achievements)

object Account {
	/**
	 * アカウントの使用実績。
	 * @param inLimited このアカウントが規制中かどうか
	 * @param loggedIn ログイン回数。
	 * @param used リクエスト成功時刻 (制限検知用)
	 */
	case class Achievements(inLimited:Boolean = false, loggedIn:Int = 0, used:Seq[Long] = Seq()) {
		/**
		 * 指定された時刻 (ミリ秒) 内にこのアカウントが使用された回数を参照します。
		 */
		def usedFor(ms:Long):Int = {
			val limit = System.currentTimeMillis() - ms
			used.count{ _ <= limit }
		}
		def toXML = <achievements>
			<inLimited>{inLimited}</inLimited>
			<loggedIn>{loggedIn}</loggedIn>
			<used>{ used.map{ tm => <tm>{tm}</tm> } }</used>
		</achievements>
	}
	object Achievements {
		def fromXML(elem:Elem):Achievements = {
			val inLimited = (elem \ "inLimited").text.toBoolean
			val loggedIn = (elem \ "loggedIn").text.toInt
			val used = (elem \ "used" \ "tm").toSeq.map{ _.text.toLong }
			Achievements(inLimited, loggedIn, used)
		}
	}
}

sealed abstract class Streaming {

}


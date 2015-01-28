/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.{InputStreamReader, FileInputStream, File, Reader}
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.util.matching.Regex

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Config
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Config private[this](parent:Option[Config], config:Map[String,String]) {
	import org.koiroha.bombaysapphire.Config._
	private[this] val formatted = new ConcurrentHashMap[String,String]()

	def this(config:Map[String,String]) = this(None, config)
	def this(parent:Config, config:Map[String,String]) = this(Some(parent), config)

	def getOrElse(key:String, default: =>String):String = get(key).getOrElse(default)

	def get(key:String):Option[String] = {
		Option(formatted.computeIfAbsent(key, new Function[String,String](){
			override def apply(key:String):String = {
				config.get(key).map { fmt => format(fmt)}.orElse(parent.flatMap{ _.get(key) }) match {
					case Some(value) =>
						logger.debug(s"$key = $value")
						value
					case None => null
				}
			}
		}))
	}

	def apply(key:String):String = get(key).get

	private[this] def format(fmt:String):String = Placeholder.replaceSomeIn(fmt, { m:Regex.Match =>
		locally {
			val p = m.group(1)
			if(p.startsWith("{") && p.endsWith("}")) p.substring(1, p.length - 1) else p
		}.split(":", 2) match {
			case Array(key, default) => Some(getOrElse(key, default))
			case Array(key) => get(key)
			case _ => None
		}
	})
}
object Config {
	private[Config] val logger = LoggerFactory.getLogger(classOf[Config])
	private[Config] val Placeholder = """\$((\{([^\}]*)\})|([\p{Alnum}\-_:]*))""".r

	private[this] lazy val env = new Config(System.getenv.toMap)
	private[this] lazy val props = new Config(env, System.getProperties.toMap)

	def apply(file:String):Config = apply(new File(file))

	def apply(file:File):Config = io.using(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)){ in =>
		apply(in)
	}

	def apply(is:Reader):Config = {
		val prop = new Properties()
		prop.load(is)
		new Config(props, prop.toMap)
	}
}
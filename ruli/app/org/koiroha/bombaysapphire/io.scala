/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import java.io.IOException

import org.slf4j.LoggerFactory

import scala.util.Try
import scala.language.reflectiveCalls

package object io {
	private[this] val logger = LoggerFactory.getLogger("org.koiroha.bombaysapphire.io")

	def using[T<:{def close()},R](c:T)(f:T=>R):R = try {
		f(c)
	} finally {
		Try{ c.close() }.recover {
			case ex:IOException =>
				logger.error(s"fail to close resource: $c", ex)
		}
	}
}

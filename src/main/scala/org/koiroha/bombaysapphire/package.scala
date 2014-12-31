/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

import scala.language.reflectiveCalls

package object io {
	def using[T <: {def close():Unit},R](r:T)(exec:(T)=>R):R = try {
		exec(r)
	} finally {
		r.close()
	}
}

/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.garuda.batch

import java.io.File

import org.koiroha.bombaysapphire.garuda.Context
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Batch
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Batch extends App {
	private[Batch] val logger = LoggerFactory.getLogger(getClass.getName.dropRight(1))

	val context = Context(new File(args.head))

	args(1) match {
		case "farm:activities" => Farms.activities(context)
	}

}


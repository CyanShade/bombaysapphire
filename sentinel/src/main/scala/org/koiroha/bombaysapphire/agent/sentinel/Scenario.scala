/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import javafx.scene.web.WebView

import org.koiroha.bombaysapphire.geom.LatLng

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Scenario
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
abstract class Scenario {
	def next():(WebView)=>Unit
}
object Scenario {
	sealed trait Command
	case class Sleep(tm:Long)
	case class Show(position:LatLng)
}

/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/

import controllers.Sentinel
import play.api._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Global
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */

object Global extends GlobalSettings {

	override def onStart(app: Application) {
		Sentinel.startBatches()
		Logger.info("Application has started")
	}

	override def onStop(app: Application) {
		Sentinel.stopBatches()
		Logger.info("Application shutdown...")
	}

}
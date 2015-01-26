/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Farm
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Farm {
	case class Point(id:Int, latE6:Int, lngE6:Int)

	// ==============================================================================================
	// クラスタの判定
	// ==============================================================================================
	/**
	 * 指定された緯度/経度に対して k-means を使用してクラスタ化します。
	 */
	def kmeans(points:Seq[Point], clusterCount:Int) = {

	}
}

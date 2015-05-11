/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package models

import org.markdown4j.Markdown4jProcessor

package object util {

	object markdown {
		// ==============================================================================================
		// Markdown でのフォーマット
		// ==============================================================================================
		/**
		 * 指定された Markdown 書式の文字列をフォーマットし HTML に変換します。
		 */
		def format(md:String):String = new Markdown4jProcessor().process(md)
	}
}

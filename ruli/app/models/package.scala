/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package models

import org.markdown4j.Markdown4jProcessor

package object farms {
	case class Summary(id:Int, name:String, address:String, description:String)
	case class Description(id:Int, name:String, address:String, kml:String, description:String,
	                       portals:Int)
	case class Edit(
		id:Int, parent:Option[Int], name:String, address:String, kml:String, description:String)
	object Edit {
		def parse(f:Tables.Farms#TableElementType):Edit = {
			Edit(f.id, f.parent, f.name, f.address, f.externalKmlUrl.getOrElse(""), f.description)
		}
	}
}

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

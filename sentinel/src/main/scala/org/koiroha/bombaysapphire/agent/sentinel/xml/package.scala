/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent.sentinel

import java.io.{OutputStreamWriter, Writer}
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.{XPathConstants, XPathFactory}

import org.w3c.dom._

import scala.annotation.tailrec

package object xml {
	implicit def _NodeList2List(nl:NodeList):Seq[Node] = for(i <- 0 until nl.getLength) yield nl.item(i)

	implicit class _Element(elem:Element){
		/** 直下の name 要素を全て取得 */
		def \*(name:String):Seq[Element] = *.filter{ _.getTagName == name }
		/** 直下の name 必須要素を取得 */
		def \(name:String):Element = \*(name).head
		/** 直下の name 任意要素を取得 */
		def \?(name:String):Option[Element] = \*(name).headOption
		/** 直下の name 任意要素を取得 (存在しない場合は作成) */
		def \+(name:String):Element = {
			\*(name).headOption.getOrElse{
				val e = elem.getOwnerDocument.createElement(name)
				elem.appendChild(e)
				e
			}
		}
		/** 子孫の name 要素を全て取得 */
		def \\*(name:String):Seq[Element] = {
			val xpath = XPathFactory.newInstance().newXPath()
			xpath.evaluate(s"//$name", elem, XPathConstants.NODESET).asInstanceOf[NodeList].toSeq.map{ _.asInstanceOf[Element] }
		}
		/** name 属性値を取得 */
		def \@(name:String):String = elem.getAttribute(name)
		def clear():Element = {
			while(elem.getChildNodes.getLength > 0){
				elem.removeChild(elem.getFirstChild)
			}
			elem
		}
		def text:String = elem.getTextContent
		def text_=(value:String):Unit = elem.clear().appendChild(elem.getOwnerDocument.createTextNode(value))
		def attr(name:String):String = elem.getAttribute(name)
		def attr(name:String, value:String):Unit = elem.setAttribute(name, value)
		def <<(e:Node):Element = {
			elem.appendChild(e)
			elem
		}
		def <<(text:String):Element = <<(elem.getOwnerDocument.createTextNode(text))
		def * = elem.getChildNodes.collect{ case e:Element => e }
	}

	implicit class _Document(doc:Document) {
		def dump():Unit = {
			val out = new OutputStreamWriter(System.err)
			dump(out)
			out.flush()
		}
		def dump(out:Writer):Unit = {
			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(out))
		}
		def prettify():Unit = {
			setIndentToChildren(1, doc.getDocumentElement)
		}
		private[this] def setIndentToChildren(i:Int, parent:Element):Unit = {
			// 前方の空白を削除
			@tailrec
			def trimLeft(elem:Option[Element]):Unit = {
				val target = elem match {
					case Some(e) => Option(e.getPreviousSibling)
					case None => Option(parent.getLastChild)
				}
				target match {
					case Some(node: Text) =>
						val text = node.getData.reverse.dropWhile {Character.isWhitespace}.reverse
						if (text.isEmpty) {
							node.getParentNode.removeChild(node)
							trimLeft(elem)
						} else if (node.getData.length != text.length) {
							node.getParentNode.replaceChild(node.getOwnerDocument.createTextNode(text), node)
						} else {
							/* */
						}
					case Some(_) =>
					case None =>
				}
			}
			parent.*.foreach{ elem =>
				trimLeft(Some(elem))
				// 前方にインデント用の空白を追加
				val idnt = elem.getOwnerDocument.createTextNode("\n" + ("  " * i))
				elem.getParentNode.insertBefore(idnt, elem)
				// 下位のノードに対して適用
				setIndentToChildren(i + 1, elem)
			}
			trimLeft(None)
			if(parent.getChildNodes.getLength > 0){
				val idnt = parent.getOwnerDocument.createTextNode("\n" + ("  " * (i-1)))
				parent.appendChild(idnt)
			}
		}
	}
	implicit class _NodeList(nl:NodeList) {
		def toList:List[Node] = (0 until nl.getLength).map{ nl.item }.toList
	}
}

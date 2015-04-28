package services

import java.io.{File, Closeable}
import scala.collection.JavaConversions._

package object io {

  def using[T <: Closeable,U](c:T)(f:(T)=>U):U = try {
    f(c)
  } finally {
    c.close()
  }

  implicit class _File(file:File){
    def getExtension:String = {
      val name = file.getName
      val i = name.lastIndexOf('.')
      if(i >= 0){
        name.substring(i+1)
      } else {
        ""
      }
    }
  }
}


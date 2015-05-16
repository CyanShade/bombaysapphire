package services

import java.io.{Closeable, File, InputStream}
import java.nio.file.Files

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
    def toByteArray:Array[Byte] = Files.readAllBytes(file.toPath)
  }


  implicit class _InputStream(in:InputStream){
    def toByteArray:Array[Byte] = Stream.continually{ in.read() }.takeWhile{ _ >= 0 }.map{ _.toByte }.toArray
  }
}


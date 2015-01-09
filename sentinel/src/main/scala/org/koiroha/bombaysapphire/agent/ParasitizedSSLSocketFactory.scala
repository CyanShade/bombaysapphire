/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package org.koiroha.bombaysapphire.agent

import java.net.{InetAddress, InetSocketAddress, Socket}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._

import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ParasitizedSSLSocketFactory
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 特定の接続先について強制的にプロキシサーバへ接続させる `SSLSocketFactory`。
 *
 * @author Takami Torao
 */
private class ParasitizedSSLSocketFactory(hostname:String, port:Int, proxy:InetSocketAddress) extends SSLSocketFactory {
  import org.koiroha.bombaysapphire.agent.ParasitizedSSLSocketFactory._

  private[this] val sc = SSLContext.getInstance("SSL")
  sc.init(null, TrustAllCerts, new SecureRandom())

  // かのサイト向けの SSL 通信を全て localhost:443 に向ける
  private[this] val root = sc.getSocketFactory

  override def getDefaultCipherSuites:Array[String] = root.getDefaultCipherSuites
  override def getSupportedCipherSuites:Array[String] = root.getSupportedCipherSuites
  override def createSocket(socket:Socket, hostname:String, port:Int, autoClose:Boolean):Socket = {
    val scs = if(hostname == this.hostname && port == this.port) {
      logger.debug(s"createSocket(socket, $hostname, $port, $autoClose) forward to ${proxy.getHostName}:${proxy.getPort}")
      socket.close()
      new Socket(proxy.getAddress, proxy.getPort)
    } else {
      socket
    }
    root.createSocket(scs, hostname, port, autoClose)
  }
  override def createSocket(hostname:String, port:Int):Socket = {
    if(hostname == this.hostname && port == this.port) {
      logger.debug(s"createSocket($hostname, $port) forward to ${proxy.getHostName}:${proxy.getPort}")
      new Socket(proxy.getAddress, proxy.getPort)
    } else {
      new Socket(hostname, port)
    }
  }
  override def createSocket(hostname:String, port:Int, local:InetAddress, localPort:Int): Socket = ???
  override def createSocket(address:InetAddress, port:Int):Socket = {
    if(address.getHostName == this.hostname && port == this.port) {
      logger.debug(s"createSocket($address, $port) forward to ${proxy.getHostName}:${proxy.getPort}")
      new Socket(proxy.getAddress, proxy.getPort)
    } else {
      new Socket(address, port)
    }
  }
  override def createSocket(address:InetAddress, port:Int, local:InetAddress, localPort:Int): Socket = ???
}

private object ParasitizedSSLSocketFactory {
  private[ParasitizedSSLSocketFactory] val logger = LoggerFactory.getLogger(classOf[ParasitizedSSLSocketFactory])

  // 自己署名証明書を含む全てのサーバ証明書を無検証で信頼する TrustManager
  val TrustAllCerts:Array[TrustManager] = Array(
    new X509TrustManager {
      override def getAcceptedIssuers:Array[X509Certificate] = null
      override def checkClientTrusted(certs:Array[X509Certificate], s:String):Unit = None
      override def checkServerTrusted(certs:Array[X509Certificate], s:String):Unit = None
    }
  )
}
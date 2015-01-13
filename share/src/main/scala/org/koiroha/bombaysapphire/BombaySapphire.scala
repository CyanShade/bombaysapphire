package org.koiroha.bombaysapphire

object BombaySapphire {
  /** 検索避けのためシーザー暗号で簡単な難読化 */
  private[this] def decode(s:String):String = s.map{ _ + 3 }.map{ _.toChar }.mkString
  /** 検索避けのためシーザー暗号で簡単な難読化 */
  private[this] def encode(s:String):String = s.map{ _ - 3 }.map{ _.toChar }.mkString
  /** かのサイトのホスト名 */
  def RemoteHost = decode("ttt+fkdobpp+`lj")   // ホスト名
  /** かのサイトのホストアドレス */
  //def RemoteAddress = decode("4/+.1+/16+.5-")  // IP アドレス

  /** 地球の外周[km] */
  private[this] val earthRound = 40000.0
  /** 1kmあたりの緯度 */
  val latUnit = 360.0 / earthRound
  /** 1kmあたりの経度 */
  def lngUnit(lat:Double):Double = latUnit * math.cos(lat / 360 * 2 * math.Pi)
}

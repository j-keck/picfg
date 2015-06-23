package picfg

import scala.collection.immutable.ListMap

object config {


  case class Prop(name: String, var value: String, description: String, validValues: Seq[String] = Seq.empty)

  trait Config {
    def name: String

    def path: String

    def props: ListMap[Symbol, Prop]

    def parseConfigFile(content: String): Either[Exception, Config]

    def generateConfigFile: String

    def updateProp(p: Prop) = {
      props.find(_._2.name == p.name).foreach { case (symbol, _) =>
        props(symbol).value = p.value
      }
    }
  }


  class WPASupplicant extends Config {
    override val name = "WLAN Authentication"
    // FIXME: this go's under /etc/wpa_supplicant/
    override val path = "/tmp/wpa_supplicant.conf"

    override val props = ListMap(
      'ESSID -> Prop("ESSID", "", "WLAN ESSID"),
      'KeyMGMT -> Prop("Key Management", "", "Key Management", Seq("NONE", "WPA-PSK"))
    )

    override def parseConfigFile(content: String): Either[Exception, Config] = {
      val SSID = """\s*ssid\s*=\s*"(.+)"\s*""".r
      val KEY_MGMT = """\s*key_mgmt\s*=\s*(.+)\s*""".r

      content.split("\n").collect{
        case SSID(ssid) => props('ESSID).value = ssid
        case KEY_MGMT(keyMgmt) => props('KeyMGMT).value = keyMgmt
      }

      Right(this)
    }

    override def generateConfigFile: String =
      s"""# DONT EDIT THIS FILE BY HAND!
         |ctrl_interface=/var/run/wpa_supplicant
         |ap_scan=1
         |
         |network={
         |  ssid="${props('ESSID).value}"
         |  key_mgmt=${props('KeyMGMT).value}
         |}
       """.stripMargin
  }

  class Network extends Config {
    override val name: String = "Network"

    // FIXME: this go's under  /etc/network/interfaces.d/picfg-wlan0
    override val path: String = "/tmp/picfg-wlan0"

    override val props = ListMap(
      'WLAN_IP -> Prop("WLAN IP", "", "Example 192.168.1.5"),
      'WLAN_NM -> Prop("WLAN Netmask", "", "Example 255.255.255.0"),
      'WLAN_GW -> Prop("WLAN Gateway", "", "Example 192.168.1.1")
    )

    override def parseConfigFile(content: String): Either[Exception, Config] = {
      val WLAN_IP = """\s*address\s+(.*)\s*""".r
      val WLAN_NM = """\s*netmask\s+(.*)\s*""".r
      val WLAN_GW = """\s*gateway\s+(.*)\s*""".r

      content.split("\n").collect{
        case WLAN_IP(ip) => props('WLAN_IP).value = ip
        case WLAN_NM(nm) => props('WLAN_NM).value = nm
        case WLAN_GW(gw) => props('WLAN_GW).value = gw
      }
      Right(this)
    }

    override def generateConfigFile: String =
      s"""# DONT EDIT THIS FILE BY HAND!
         |iface wlan0 inet static
         |  address ${props('WLAN_IP).value}
         |  netmask ${props('WLAN_NM).value}
         |  gateway ${props('WLAN_GW).value}
         |""".stripMargin
  }


  val Configurations: Seq[Config] = Seq(new WPASupplicant, new Network)


}

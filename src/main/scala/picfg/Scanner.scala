package picfg

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress}

import sodium.StreamSink

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Scanner {
  val findings = new StreamSink[Pi]

  def scanNetwork(port: Int, timeout: Duration)(implicit ec: ExecutionContext): Future[Unit]
}


object Scanner extends Scanner {

  def scanNetwork(port: Int, timeout: Duration)(implicit ec: ExecutionContext): Future[Unit] = Future {

    // setup socket
    val socket = new DatagramSocket()
    socket.setBroadcast(true)
    socket.setSoTimeout(timeout.toMillis.toInt)

    // send
    val msg = "picfg-ping".getBytes
    //FIXME: broadcast address: 0.0.0.0 or 255.255.255.255 doesn't work - why?
    val packet = new DatagramPacket(msg, msg.length, new InetSocketAddress("192.168.1.255", port))
    socket.send(packet)

    // receive
    while (true) {
      val buf = new Array[Byte](256)
      val rec = new DatagramPacket(buf, buf.length)
      socket.receive(rec)
      val msg = new String(buf, 0, rec.getLength)
      val Array(name, ip) = msg.split(":")
      findings.send(Pi(name, ip))
    }
  }
}

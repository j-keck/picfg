package picfg

import java.io.ByteArrayInputStream

import com.jcraft.jsch.{ChannelSftp, JSch, Session}
import picfg.config.Config

import scala.io.Source
import scala.reflect.runtime.universe.{Try => _, _}
import scala.util.Try

class Remote(pi: Pi, user: String, pwd: String) extends LogSupport {

  private val scp = new SCP(pi.ip, user, pwd)

  def fetch[A: TypeTag](configs: Seq[Config]): Either[Exception, Seq[Config]] = {
    def flatten[L, R](seq: Seq[Either[L, R]]): Either[L, Seq[R]] = {
      seq.foldLeft(Right(Seq.empty): Either[L, Seq[R]]) { (z, e) =>
        e.fold(Left(_), ev => z.right.map(_ :+ ev))
      }
    }

    scp.connect().right.flatMap { _ =>
      val seq: Seq[Either[Exception, Config]] = configs.map { config =>
        for {
          content <- scp.read(config.path).right
          config_ <- config.parseConfigFile(content).right
        } yield config_
      }
      scp.disconnect()

      flatten(seq)
    }
  }


  def push(configs: Seq[Config]): Either[Exception, Unit] = {
    scp.connect().right.flatMap { _ =>
      configs.foldLeft(Right(()): Either[Exception, Unit]) { (z, c) =>
        scp.send(c.generateConfigFile, c.path)
      }
      scp.disconnect()
    }
  }


  class SCP(ip: String, user: String, pwd: String) {
    private val jsch = new JSch();

    private var session: Session = _


    // FIXME: add timeout -> session.connect(timeout)!
    def connect(): Either[Exception, Unit] = Try {
      session = jsch.getSession(user, ip, 22)
      //FIXME: bad!!!
      session.setConfig("StrictHostKeyChecking", "no")
      session.setPassword(pwd)
      session.connect()
    }.toEither

    def disconnect(): Either[Exception, Unit] = Try(session.disconnect()).toEither

    def send(content: String, path: String): Either[Exception, Unit] =
      withSftpChannel { channel =>
        logInfo(s"send ${path} to ${pi}")
        // we send only small config file's so we don't expect to much memory consuming here
        val is = new ByteArrayInputStream(content.getBytes)
        channel.put(is, path, ChannelSftp.OVERWRITE)
      }


    def read(path: String): Either[Exception, String] =
      withSftpChannel { channel =>
        logInfo(s"read ${path} from ${pi}")
        Source.fromInputStream(channel.get(path)).getLines().mkString("\n")
      }.left.flatMap(_ match {
        // if the file doesn't exists, start with a empty configuration
        case e: Exception if e.getMessage.contains("No such file") => Right("")
        case e => Left(e)
      })


    private def withSftpChannel[A](f: ChannelSftp => A): Either[Exception, A] = {
      var channel: ChannelSftp = null
      try {
        assert(session.isConnected, "session not connected")
        channel = session.openChannel("sftp").asInstanceOf[ChannelSftp]
        channel.connect()
        val res = f(channel)
        channel.disconnect()
        Right(res)
      } catch {
        case e: Exception =>
          if (channel != null && channel.isConnected)
            channel.disconnect()

          Left(e)
      }
    }


  }

}

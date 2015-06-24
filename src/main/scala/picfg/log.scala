package picfg

import java.text.SimpleDateFormat
import java.util.Date

import sodium.StreamSink

object log {

  sealed trait Log{
    val ts = new Date()
  }

  case class Info(msg: String) extends Log

  case class Error(msg: String, e: Exception) extends Log


  private object LogSupport {
    // stream for log events
    val log = new StreamSink[Log]
  }

  trait LogSupport {
    def onLogMsg(f: Log => Unit): Unit = {
      LogSupport.log.map(f)
    }

    def logInfo(msg: String) = LogSupport.log.send(Info(msg))

    def logError(msg: String, e: Exception) = LogSupport.log.send(Error(msg, e))
  }

}

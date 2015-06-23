import java.util
import java.util.concurrent.TimeoutException
import javax.swing.SwingUtilities

import sodium.{Lambda1, Handler, StreamSink}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

package object picfg {

  case class Pi(name: String, ip: String) {
    override def toString() = s"$name ($ip)"
  }


  def swingUtilsInvokeLater(f: => Unit): Unit = {
    SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        f
      }
    })
  }

  implicit def function2Handler[A, B](f: Function1[A, B]): Handler[A] = new Handler[A] {
    override def run(a: A): Unit = {
      f(a)
      ()
    }
  }

  implicit def function2Lambda[A, B](f: Function[A, B]): Lambda1[A, B] = new Lambda1[A, B] {
    override def apply(a: A): B = f(a)
  }

  sealed trait Log

  case class Info(msg: String) extends Log

  case class Error(msg: String, e: Exception) extends Log


  object LogSupport {
    // stream for log events
    val log = new StreamSink[Log]
  }

  trait LogSupport {
    val log = LogSupport.log

    def logInfo(msg: String) = log.send(Info(msg))

    def logError(msg: String, e: Exception) = log.send(Error(msg, e))
  }

  implicit class FutureOps[T](f: Future[T]) {

    /**
     * Future which throws a {@code scala.concurrent.TimeoutException} after a given duration
     */
    def timeout(duration: Duration)(implicit ec: ExecutionContext): Future[T] = {
      val p = Promise[T]
      Future {
        Try(Await.ready(never, duration))
        p.failure(new TimeoutException(s"Future timeout after ${duration}"))
      }
      Future.firstCompletedOf(Seq(p.future, f))
    }

    /**
     * Future which never completes
     */
    def never: Future[Unit] = Promise[Unit].future
  }

  implicit class ProductOps(p: Product) {

    def asJavaVector: java.util.Vector[Any] = {
      val jvector = new util.Vector[Any]()
      p.productIterator.foreach(jvector.addElement)
      jvector
    }
  }

  implicit class TryOps[T](t: Try[T]) {
    def toEither: Either[Exception, T] = t match {
      case Success(v) => Right(v)
      case Failure(t) => t match {
        // only catch exceptions, rethrow errors
        case e: Exception => Left(e)
        case _ => throw t
      }
    }
  }


}

import java.util
import java.util.concurrent.TimeoutException

import sodium.StreamSink

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

package object picfg {

  case class Pi(name: String, ip: String) {
    override def toString() = s"$name ($ip)"
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

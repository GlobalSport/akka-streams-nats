package com.mycoachsport.helpers

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

trait FutureHelper {
  implicit class AwaitableWithDuration[T](awaitable: Future[T]) {
    def retry(times: Int)(implicit executor: ExecutionContext): Future[T] = {
      awaitable recoverWith {
        case ex if times > 0 =>
          println(s" exception , remaining retries $times")
          new AwaitableWithDuration(awaitable).retry(times - 1)

        case ex =>
          println("exception, no remaining retries")
          Future.failed(ex)
      }

    }
  }
}

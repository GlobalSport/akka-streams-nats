package com.mycoachsport

import akka.actor.ActorSystem
import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{ConsumerContext, Message}
import java.time.Duration
import scala.util.control.NonFatal
import scala.concurrent.duration._

class JetStreamSourceStageV2(
    consumerContext: ConsumerContext,
    pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30),
    maxRetries: Int = 3
) extends GraphStage[SourceShape[Message]] {

  val out: Outlet[Message] = Outlet[Message]("JetstreamSource.out")
  override def shape: SourceShape[Message] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with StageLogging {

      val scheduleKey = "retryFetch"
      private var retryCount = 0

      private def tryFetchMessage(): Option[Message] = {
        try {
          val message = consumerContext.next(pullMessageTimeout)
          retryCount = 0
          Option(message)
        } catch {
          case NonFatal(ex) =>
            if (retryCount < maxRetries) {
              log.error(s"error received $ex, number of retries $retryCount")
              retryCount = retryCount + 1
              None
            } else {
              scheduleOnce(scheduleKey, 500.millis)
              log.error(s"number of retries exceeded, throwing${ex.getMessage}")

              throw ex
            }

        }
      }

      private def processMessage(maybeMessage: Option[Message]): Unit = {
        maybeMessage match {
          case Some(message) =>
            push(out, message)
          case None =>
            log.debug("No message received, scheduling retry")
            scheduleOnce(scheduleKey, 500.millis)
        }
      }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            val maybeMessage = tryFetchMessage()
            processMessage(maybeMessage)
          }
        }
      )

      override def onTimer(timerKey: Any): Unit = timerKey match {
        case scheduleKey =>
          if (isAvailable(out)) {
            val maybeMessage = tryFetchMessage()
            processMessage(maybeMessage)
          }
      }
    }
}

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
    pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30)
) extends GraphStage[SourceShape[Message]] {

  val out: Outlet[Message] = Outlet[Message]("JetstreamSource.out")
  override def shape: SourceShape[Message] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with StageLogging {
      private def tryFetchMessage(): Option[Message] = {
        try {
          val message = consumerContext.next(pullMessageTimeout)
          Option(message)
        } catch {
          case NonFatal(ex) =>
            None
        }
      }

      private def processMessage(maybeMessage: Option[Message]): Unit = {
        maybeMessage match {
          case Some(message) =>
            push(out, message)
          case None =>
            log.info("No message received, scheduling retry")
            scheduleOnce("retryFetch", 500.millis)
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
        case "retryFetch" =>
          if (isAvailable(out)) {
            val maybeMessage = tryFetchMessage()
            processMessage(maybeMessage)
          }
      }
    }
}

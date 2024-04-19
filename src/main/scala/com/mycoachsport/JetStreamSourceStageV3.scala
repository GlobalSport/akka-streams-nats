package com.mycoachsport

import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{ConsumerContext, Message}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Success}
import helpers.FutureHelper

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._

class JetStreamSourceStageV2(
    consumerContext: ConsumerContext,
    pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30),
    maxRetries: Int = 3
)(implicit executionContext: ExecutionContext)
    extends GraphStage[SourceShape[Message]]
    with FutureHelper {

  val out: Outlet[Message] = Outlet[Message]("JetstreamSource.out")
  override def shape: SourceShape[Message] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with StageLogging {
      private var callback: AsyncCallback[Unit] = _
      private val currentMessage = new AtomicReference[Option[Message]](None)
      private var asyncCallInProgress = false
      private val retryFetchKey = "retryFetch"

      private def fetchMessageAsync(): Unit = {
        log.debug("trying to fetch a message")
        asyncCallInProgress = true
        val future = Future(
          blocking(Option(consumerContext.next(pullMessageTimeout)))
        )

        future.retry(maxRetries).onComplete {
          case Success(Some(message)) =>
            asyncCallInProgress = false
            currentMessage.set(Some(message))
            if (isAvailable(out)) callback.invoke(())

          case Success(None) =>
            log.debug("No message received, retrying...")
            asyncCallInProgress = false
            scheduleOnce(retryFetchKey, 500.millis)

          case Failure(ex) =>
            log.error("Failed to fetch message after retries.")
            asyncCallInProgress = false
            failStage(ex)
        }
      }

      private def pushMessage(): Unit = {
        currentMessage.getAndSet(None).foreach { message =>
          push(out, message)
        }
      }

      override def preStart(): Unit = {
        callback = getAsyncCallback[Unit](_ => pushMessage())
        fetchMessageAsync()
      }

      setHandler(
        out,
        new OutHandler {

          override def onPull(): Unit = {

            if (currentMessage.get().nonEmpty) {
              callback.invoke(())
            }

            if (currentMessage.get().isEmpty && !asyncCallInProgress) {
              fetchMessageAsync()
            }
          }

        }
      )

      override def onTimer(timerKey: Any): Unit = timerKey match {
        case retryFetchKey =>
          fetchMessageAsync()
      }
    }
}

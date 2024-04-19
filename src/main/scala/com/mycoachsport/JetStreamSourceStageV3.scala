package com.mycoachsport

import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{ConsumerContext, Message}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Success}

import java.util.concurrent.atomic.{
  AtomicBoolean,
  AtomicInteger,
  AtomicReference
}
import scala.concurrent.duration._

class JetStreamSourceStageV3(
    consumerContext: ConsumerContext,
    pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30),
    maxRetries: Int = 3
)(implicit executionContext: ExecutionContext)
    extends GraphStage[SourceShape[Message]] {

  val out: Outlet[Message] = Outlet[Message]("JetstreamSource.out")
  override def shape: SourceShape[Message] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with StageLogging {
      private var callback: AsyncCallback[Unit] = _
      private var callbackFail: AsyncCallback[Throwable] = _
      private val currentMessage =
        new AtomicReference[Option[Message]](Option.empty)
      private val retryCount = new AtomicInteger(0)
      private val retryFetchKey = "retryFetch"
      private val asyncCallInProgress = new AtomicBoolean(false)

      private def fetchMessageAsync(): Unit = {
        log.debug("Trying to fetch a message")
        asyncCallInProgress.set(true)
        val future = Future(
          blocking(Option(consumerContext.next(pullMessageTimeout)))
        )

        future.onComplete {
          case Success(Some(message)) =>
            asyncCallInProgress.set(false)
            currentMessage.set(Some(message))
            retryCount.set(0)
            if (isAvailable(out)) callback.invoke(())

          case Success(None) =>
            log.debug("No message received, retrying...")
            asyncCallInProgress.set(false)
            retryCount.set(0)
            scheduleOnce(retryFetchKey, 500.millis)

          case Failure(ex) =>
            asyncCallInProgress.set(false)
            log.error("Fetch failed, checking retry logic.")
            callbackFail.invoke(ex)

        }(executionContext)
      }

      private def pushMessage(): Unit = {
        asyncCallInProgress.set(false)
        currentMessage.getAndSet(Option.empty).foreach(push(out, _))
      }

      override def preStart(): Unit = {
        callback = getAsyncCallback[Unit](_ => pushMessage())
        callbackFail = getAsyncCallback[Throwable] { ex =>
          if (retryCount.getAndIncrement() < maxRetries) {
            log.debug(s"Retrying fetch (${retryCount.get()} attempts made).")
            scheduleOnce(retryFetchKey, 500.millis)
          } else {
            log.error("Maximum retries reached, failing stage.")
            failStage(ex)
          }
        }
        fetchMessageAsync()
      }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            currentMessage.get() match {
              case Some(_)                            => callback.invoke(())
              case None if !asyncCallInProgress.get() => fetchMessageAsync()
              case _                                  => ()
            }
          }
        }
      )

      override def onTimer(timerKey: Any): Unit = timerKey match {
        case retryFetchKey => fetchMessageAsync()
      }
    }
}

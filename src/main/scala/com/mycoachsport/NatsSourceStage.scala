/*
 * Copyright 2020 MyCoach SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.mycoachsport

import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{Connection, Dispatcher, Message}

import java.nio.charset.StandardCharsets
import scala.collection.mutable
class NatsSourceStage(natsSettings: NatsSettings, messageBufferSize: Int)
    extends GraphStage[SourceShape[NatsMessage]] {

  val out: Outlet[NatsMessage] = Outlet[NatsMessage]("NatsSource.out")

  override def shape: SourceShape[NatsMessage] = SourceShape.of(out)

  private val queue = mutable.Queue[NatsMessage]()

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      val connection: Connection = natsSettings.connection
      val consumeMessage: AsyncCallback[Message] = getAsyncCallback(
        handleIncomingMessage
      )

      val d: Dispatcher =
        connection.createDispatcher(consumeMessage.invoke)

      var subscriptionState: SubscriptionState = Stopped

      def handleIncomingMessage(message: Message): Unit = {
        val msg = NatsMessage(
          new String(message.getData, StandardCharsets.UTF_8)
        )

        if (isAvailable(out)) {
          push(out, msg)
        } else {
          if (queue.size + 2 > messageBufferSize) {
            if (subscriptionState == Started) {
              queue.enqueue(msg)
              log.error(
                s"Stopping nats subscription - buffer size is ${queue.size} / ${messageBufferSize}"
              )
              subscriptionState =
                unsubscribeFromAllTopics(d, natsSettings.topics)
            }
          } else {
            queue.enqueue(msg)
          }
        }
      }

      subscriptionState = subscribeToAllTopics(d, natsSettings.topics)

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            if (queue.nonEmpty) {
              push(out, queue.dequeue())
              val numberOfSlotAvailable = messageBufferSize - queue.size
              // We restart the subscription if the state is stopped
              // And there is more thant 10% of the slots available in the buffer
              if (
                subscriptionState == Stopped && (numberOfSlotAvailable >= messageBufferSize * 0.10)
              ) {
                log.info(
                  s"Restarting nats subscription - buffer size is ${queue.size} / ${messageBufferSize}"
                )
                subscriptionState = subscribeToAllTopics(d, natsSettings.topics)
              }
            }
          }
        }
      )
    }

  def subscribeToAllTopics(
                            dispatcher: Dispatcher,
                            natsSubscriptions: Set[NatsSubscription]
                          ): SubscriptionState = {
    natsSubscriptions.foreach(s => subscribeToTopic(dispatcher, s))
    Started
  }

  def subscribeToTopic(
                        dispatcher: Dispatcher,
                        natsSubscription: NatsSubscription
                      ): Unit = {
    natsSubscription match {
      case SubjectSubscription(subject) => dispatcher.subscribe(subject)
      case QueueGroupSubscription(subject, group) =>
        dispatcher.subscribe(subject, group)
    }
  }

  def unsubscribeFromAllTopics(
                                dispatcher: Dispatcher,
                                natsSubscriptions: Set[NatsSubscription]
                              ): SubscriptionState = {
    natsSubscriptions.foreach(s => dispatcher.unsubscribe(s.subject))
    Stopped
  }
}

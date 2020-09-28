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

import java.nio.charset.StandardCharsets

import akka.stream.stage.{
  AsyncCallback,
  GraphStage,
  GraphStageLogic,
  OutHandler
}
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{Connection, Dispatcher, Message}

import scala.collection.mutable

class NatsSourceStage(natsSettings: NatsSettings, messageBufferSize: Int)
    extends GraphStage[SourceShape[NatsMessage]] {

  val out: Outlet[NatsMessage] = Outlet[NatsMessage]("NatsSource.out")

  override def shape: SourceShape[NatsMessage] = SourceShape.of(out)

  private val queue = mutable.Queue[NatsMessage]()

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      val connection: Connection = natsSettings.connection
      val consumeMessage: AsyncCallback[Message] = getAsyncCallback(
        handleIncomingMessage
      )

      val d: Dispatcher =
        connection.createDispatcher(consumeMessage.invoke)

      def handleIncomingMessage(message: Message): Unit = {
        val msg = NatsMessage(
          new String(message.getData, StandardCharsets.UTF_8)
        )

        if (isAvailable(out)) {
          push(out, msg)
        } else {
          if (queue.size + 1 > messageBufferSize) {
            failStage(
              new RuntimeException(
                s"Reached maximum buffer size $messageBufferSize"
              )
            )
          } else {
            queue.enqueue(msg)
          }

        }
      }

      natsSettings.topics.foreach(s => subscribeToNats(d, s))

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (queue.nonEmpty) {
            push(out, queue.dequeue())
          }
        }
      })
    }

  def subscribeToNats(dispatcher: Dispatcher,
                      natsSubscription: NatsSubscription): Unit = {
    natsSubscription match {
      case SubjectSubscription(subject) => dispatcher.subscribe(subject)
      case QueueGroupSubscription(subject, group) =>
        dispatcher.subscribe(subject, group)
    }
  }
}

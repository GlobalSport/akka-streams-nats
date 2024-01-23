package com.mycoachsport

import akka.stream.scaladsl.Source
import io.nats.client.ConsumerContext

object JetStreamSource {

  /** Create a new jetstream source from a consumer context.
    *
    * The source will get the next message onPull.
    *
    * You are responsible for managing Ack's and Nak's.
    *
    * @param consumerContext the consumer context to retrieve messages
    * @return a Jetstrem source
    */
  def apply(consumerContext: ConsumerContext) = {
    Source.fromGraph(new JetStreamSourceStage(consumerContext))
  }

}

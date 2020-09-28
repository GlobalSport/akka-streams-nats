[![Build Status](https://travis-ci.org/GlobalSport/mycoach-rfc2445.svg?branch=develop)](https://travis-ci.org/GlobalSport/akka-streams-nats)

# Akka streams nats
This library allows to use nats as an akka streams source by wrapping the official java nats client.
It currently supports subject subscriptions and queue groups subscriptions.

**It's an akka-streams source connector for NATS and not STAN nor NATS streaming**

The library using an in-memory message buffer (queue), in the case of a slow consumer message will get buffered until 
the size of the buffer is reached. If the size of the buffer is reached an exception will be thrown.

# SBT
```scala
libraryDependencies += "com.mycoachsport" %% "akka-streams-nats" % "0.0.1"
```

# maven
## Scala 2.12.x
```xml
<dependency>
    <groupId>com.mycoachsport</groupId>
    <artifactId>akka-streams-nats_2.12</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Scala 2.13.x
```xml
<dependency>
    <groupId>com.mycoachsport</groupId>
    <artifactId>akka-streams-nats_2.13</artifactId>
    <version>0.0.1</version>
</dependency>
```

# Samples
```scala

val natsConnection =
        Nats.connect("nats://localhost:4222")

    // Create source with a subject
      val natsSettings =
        NatsSettings(natsConnection, SubjectSubscription("my.subject"))

      NatsSource(natsSettings, 10)
        .map { message =>
          // Do some work
          println(message)
        }
        .run()

    // Create a queue group based source
      val natsSettingsQueue =
        NatsSettings(natsConnection, QueueGroupSubscription("my.subject", "my.queue.group"))

      NatsSource(natsSettingsQueue, 10)
        .map { message =>
          // Do some work
          println(message)
        }
        .run()
```

## LICENSE
[MIT](LICENSE)
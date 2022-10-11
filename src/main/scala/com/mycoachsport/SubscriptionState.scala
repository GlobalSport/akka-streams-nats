package com.mycoachsport

sealed trait SubscriptionState

case object Started extends SubscriptionState

case object Stopped extends SubscriptionState

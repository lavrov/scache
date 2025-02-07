package com.evolutiongaming.scache

import cats.effect.{Resource, Sync}
import cats.implicits._
import com.evolutiongaming.smetrics.MetricsHelper._
import com.evolutiongaming.smetrics.{CollectorRegistry, LabelNames, Quantile, Quantiles}

import scala.concurrent.duration.FiniteDuration

object CacheMetrics {

  type Name = String

  type Prefix = String

  object Prefix {
    val Default: Prefix = "cache"
  }


  def of[F[_] : Sync](
    collectorRegistry: CollectorRegistry[F],
    prefix: Prefix = Prefix.Default,
  ): Resource[F, Name => Cache.Metrics[F]] = {

    val getCounter = collectorRegistry.counter(
      name = s"${ prefix }_get",
      help = "Get type: hit or miss",
      labels = LabelNames("name", "type"))

    val loadResultCounter = collectorRegistry.counter(
      name = s"${ prefix }_load_result",
      help = "Load result: success or failure",
      labels = LabelNames("name", "result"))

    val loadTimeSummary = collectorRegistry.summary(
      name = s"${ prefix }_load_time",
      help = s"Load time in seconds",
      quantiles = Quantiles(
        Quantile(value = 0.9, error = 0.05),
        Quantile(value = 0.99, error = 0.005)),
      labels = LabelNames("name", "result"))

    val sizeGauge = collectorRegistry.gauge(
      name = s"${ prefix }_size",
      help = s"Cache size",
      labels = LabelNames("name"))

    for {
      getsCounter       <- getCounter
      loadResultCounter <- loadResultCounter
      loadTimeSummary   <- loadTimeSummary
      sizeGauge         <- sizeGauge
    } yield {
      name: Name =>

        new Cache.Metrics[F] {

          def get(hit: Boolean) = {
            val hitOrMiss = if (hit) "hit" else "miss"
            getsCounter.labels(name, hitOrMiss).inc()
          }

          def size(size: Int) = {
            sizeGauge.labels(name).set(size.toDouble)
          }

          def load(time: FiniteDuration, success: Boolean) = {
            val successOrFailure = if (success) "success" else "failure"
            for {
              _ <- loadResultCounter.labels(name, successOrFailure).inc()
              _ <- loadTimeSummary.labels(name, successOrFailure).observe(time.toNanos.nanosToSeconds)
            } yield {}
          }
        }
    }
  }
}
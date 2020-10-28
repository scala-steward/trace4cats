package io.janstenpickle.trace4cats.sampling.tail.cache

import cats.data.NonEmptyList
import cats.effect.{Clock, Sync}
import cats.syntax.functor._
import com.github.blemale.scaffeine.Scaffeine
import io.janstenpickle.trace4cats.model.{SampleDecision, TraceId}
import io.janstenpickle.trace4cats.sampling.tail.SampleDecisionStore

import scala.concurrent.duration._

object LocalCacheSampleDecisionStore {
  def apply[F[_]: Sync: Clock](ttl: FiniteDuration = 5.minutes, maximumSize: Option[Long]): F[SampleDecisionStore[F]] =
    Sync[F]
      .delay {
        val builder = Scaffeine().expireAfterAccess(ttl)

        maximumSize.fold(builder)(builder.maximumSize).build[TraceId, SampleDecision]()
      }
      .map { cache =>
        new SampleDecisionStore[F] {
          override def getDecision(traceId: TraceId): F[Option[SampleDecision]] =
            Sync[F].delay(cache.getIfPresent(traceId))

          override def storeDecision(traceId: TraceId, sampleDecision: SampleDecision): F[Unit] =
            Sync[F].delay(cache.put(traceId, sampleDecision))

          override def batch(traceIds: NonEmptyList[TraceId]): F[Map[TraceId, SampleDecision]] =
            Sync[F].delay(cache.getAllPresent(traceIds.toList))

          override def storeDecisions(decisions: Map[TraceId, SampleDecision]): F[Unit] =
            Sync[F].delay(cache.putAll(decisions))
        }
      }
}
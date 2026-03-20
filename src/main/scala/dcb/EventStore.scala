package dcb

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.circe.Json
import java.time.Instant
import skunk.*

import skunk.implicits.sql
import cats.instances.int
import skunk.codec.all._
import skunk.circe.codec.all.jsonb
import cats.instances.string
import skunk.data.TransactionIsolationLevel
import skunk.data.TransactionAccessMode
import skunk.exception.SkunkException
// import skunk.syntax.all.sql
// import skunk.syntax.stringcontext.sql

/** Concurrency conflict: someone appended relevant events after we last read.
  */
class ConflictException(
    val expectedSequenceNumber: Long,
    val actualSequenceNumber: Long
) extends RuntimeException(
      s"Conflict: expected sequence number <= $expectedSequenceNumber but log is at $actualSequenceNumber"
    )

class EventStore(pool: Resource[IO, Session[IO]]):
  import EventStore.*

  def queryByTags(tags: Set[Tag]): IO[EventStream] =
    pool.use { session =>
      for
        lastSeq <- session.unique(lastSeqQ)
        events <-
          if tags.isEmpty then IO.pure(List.empty[Event])
          else
            val tagList = tags.map(_.value).toList
            session.execute(eventsByTagsQ(tagList.size))(tagList).map(_.toList)
      yield EventStream(events, lastSeq)
    }

  def append(
      newEvents: List[NewEvent],
      tags: Set[Tag],
      expectedLastSequenceNumber: Long,
      maxRetries: Int = 3
  ): IO[List[Event]] =
    if newEvents.isEmpty then IO.pure(List.empty)
    else
      pool
        .use { session =>
          session
            .transaction(
              TransactionIsolationLevel.Serializable,
              TransactionAccessMode.ReadWrite
            )
            .use { _ =>
              for
                _ <- checkForConflicts(
                  session,
                  tags,
                  expectedLastSequenceNumber
                )
                appended <- newEvents.traverse { evt =>
                  session.unique(insertEventQ)(
                    evt.eventType *: tagsToJson(
                      evt.tags
                    ) *: evt.payload *: EmptyTuple
                  )
                }
              yield appended
            }
        }
        .recoverWith {
          case _: SkunkException if maxRetries > 0 =>
            append(newEvents, tags, expectedLastSequenceNumber, maxRetries - 1)
        }

  def fetchAfter(afterSequenceNumber: Long, limit: Int = 100): IO[List[Event]] =
    pool.use { session =>
      session
        .execute(fetchAfterQ)(afterSequenceNumber *: limit.toLong *: EmptyTuple)
        .map(_.toList)
    }

  private def checkForConflicts(
      session: Session[IO],
      tags: Set[Tag],
      expectedLastSequenceNumber: Long
  ): IO[Unit] =
    if tags.isEmpty then IO.unit
    else
      val tagList = tags.map(_.value).toList
      for
        conflictCount <- session.unique(conflictCountQ(tagList.size))(
          expectedLastSequenceNumber *: tagList *: EmptyTuple
        )
        _ <-
          if conflictCount > 0 then
            session.unique(lastSeqQ).flatMap { actual =>
              IO.raiseError(
                ConflictException(expectedLastSequenceNumber, actual)
              )
            }
          else IO.unit
      yield ()

  private def tagsToJson(tags: Set[Tag]): Json =
    Json.arr(tags.map(t => Json.fromString(t.value)).toSeq*)

object EventStore:
  private val tagsCodec: Codec[Set[Tag]] = jsonb.imap { json =>
    json.asArray
      .map(_.flatMap(_.asString).map(Tag(_)).toSet)
      .getOrElse(Set.empty)
  } { tags =>
    Json.arr(tags.map(t => Json.fromString(t.value)).toSeq*)
  }

  private val eventDecoder: Decoder[Event] =
    (int8 *: text *: tagsCodec *: jsonb *: timestamptz).map {
      case seq *: eventType *: tags *: payload *: recordedAt *: EmptyTuple =>
        Event(seq, eventType, tags, payload, recordedAt.toInstant)
    }

  private val lastSeqQ: Query[Void, Long] =
    sql"SELECT COALESCE(MAX(sequence_number), 0) FROM events".query(int8)

  /** Build a parameterized query for N tags using jsonb_exists_any. */
  private def eventsByTagsQ(n: Int): Query[List[String], Event] =
    sql"""SELECT sequence_number, event_type, tags, payload, recorded_at
          FROM events
          WHERE tags ?| ARRAY[${text.list(n)}]::text[]
          ORDER BY sequence_number ASC"""
      .query(eventDecoder)

  private val insertEventQ: Query[String *: Json *: Json *: EmptyTuple, Event] =
    sql"""INSERT INTO events (event_type, tags, payload)
          VALUES ($text, $jsonb, $jsonb)
          RETURNING sequence_number, event_type, tags, payload, recorded_at"""
      .query(eventDecoder)

  private val fetchAfterQ: Query[Long *: Long *: EmptyTuple, Event] =
    sql"""SELECT sequence_number, event_type, tags, payload, recorded_at
          FROM events
          WHERE sequence_number > $int8
          ORDER BY sequence_number ASC
          LIMIT $int8"""
      .query(eventDecoder)

  /** Build a parameterized conflict count query for N tags. */
  private def conflictCountQ(
      n: Int
  ): Query[Long *: List[String] *: EmptyTuple, Long] =
    sql"""SELECT COUNT(*) FROM events
          WHERE sequence_number > $int8
          AND tags ?| ARRAY[${text.list(n)}]::text[]"""
      .query(int8)

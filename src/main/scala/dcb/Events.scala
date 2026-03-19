package dcb

import io.circe.Json
import java.time.Instant

/** A tag identifies an entity concern, e.g. "Course:CS101" or "Student:42". */
case class Tag(value: String)

/** A persisted event from the event log. */
case class Event(
    sequenceNumber: Long,
    eventType: String,
    tags: Set[Tag],
    payload: Json,
    recordedAt: Instant
)

/** An event to be appended (before it gets a sequence number). */
case class NewEvent(
    eventType: String,
    tags: Set[Tag],
    payload: Json
)

object NewEvent:
  /** Create a NewEvent from a typed domain event case class. Derives eventType
    * from the class name, payload from the Encoder.
    */
  def from[E <: Product: io.circe.Encoder](event: E, tags: Set[Tag]): NewEvent =
    NewEvent(
      eventType = event.productPrefix,
      tags = tags,
      payload = io.circe.Encoder[E].apply(event)
    )

/** Returned when querying events — includes the last sequence number seen. */
case class EventStream(
    events: List[Event],
    lastSequenceNumber: Long
)

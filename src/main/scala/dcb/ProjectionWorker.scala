package dcb

import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*

/** A background worker that polls the event log for new events and projects
  * them using the given handler. Tracks its cursor in the `projection_cursors`
  * table so it can resume after restarts and supports full replay by resetting
  * the cursor.
  */
class ProjectionWorker(
    name: String,
    eventStore: EventStore,
    eventLogXa: Transactor[IO],
    project: Event => IO[Unit],
    pollInterval: FiniteDuration = 100.millis,
    batchSize: Int = 100
):

  /** Run the projection loop forever. Intended to be started as a background
    * fiber. Processes events in batches, advancing the cursor after each event.
    */
  def run: IO[Nothing] =
    getCursor.flatMap(loop)

  /** Reset the cursor to 0 so the next run replays all events from the start.
    */
  def reset: IO[Unit] =
    sql"""UPDATE projection_cursors SET last_sequence_number = 0, updated_at = now()
          WHERE projection_name = $name""".update.run.transact(eventLogXa).void

  private def loop(cursor: Long): IO[Nothing] =
    eventStore.fetchAfter(cursor, batchSize).flatMap { events =>
      if events.isEmpty then IO.sleep(pollInterval) *> loop(cursor)
      else
        events
          .traverse_ { event =>
            project(event) *> advanceCursor(event.sequenceNumber)
          }
          .flatMap(_ => loop(events.last.sequenceNumber))
    }

  private def getCursor: IO[Long] =
    sql"""INSERT INTO projection_cursors (projection_name, last_sequence_number)
          VALUES ($name, 0)
          ON CONFLICT (projection_name) DO NOTHING""".update.run
      .transact(eventLogXa) *>
      sql"SELECT last_sequence_number FROM projection_cursors WHERE projection_name = $name"
        .query[Long]
        .unique
        .transact(eventLogXa)

  private def advanceCursor(sequenceNumber: Long): IO[Unit] =
    sql"""UPDATE projection_cursors SET last_sequence_number = $sequenceNumber, updated_at = now()
          WHERE projection_name = $name""".update.run.transact(eventLogXa).void

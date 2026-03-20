package dcb

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import scala.concurrent.duration.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*

/** Prepared statements for projection cursor management. */
object ProjectionWorker:
  private val resetCursorCmd: Command[String] =
    sql"""UPDATE projection_cursors SET last_sequence_number = 0, updated_at = now()
          WHERE projection_name = $text""".command

  private val upsertCursorCmd: Command[String] =
    sql"""INSERT INTO projection_cursors (projection_name, last_sequence_number)
          VALUES ($text, 0)
          ON CONFLICT (projection_name) DO NOTHING""".command

  private val getCursorQ: Query[String, Long] =
    sql"SELECT last_sequence_number FROM projection_cursors WHERE projection_name = $text"
      .query(int8)

  private val advanceCursorCmd: Command[Long *: String *: EmptyTuple] =
    sql"""UPDATE projection_cursors SET last_sequence_number = $int8, updated_at = now()
          WHERE projection_name = $text""".command

/** A background worker that polls the event log for new events and projects
  * them using the given handler. Tracks its cursor in the `projection_cursors`
  * table so it can resume after restarts and supports full replay by resetting
  * the cursor.
  */
class ProjectionWorker(
    name: String,
    eventStore: EventStore,
    pool: Resource[IO, Session[IO]],
    project: Event => IO[Unit],
    pollInterval: FiniteDuration = 100.millis,
    batchSize: Int = 100
):
  import ProjectionWorker.*

  /** Run the projection loop forever. Intended to be started as a background
    * fiber. Processes events in batches, advancing the cursor after each event.
    */
  def run: IO[Nothing] =
    getCursor.flatMap(loop)

  /** Reset the cursor to 0 so the next run replays all events from the start.
    */
  def reset: IO[Unit] =
    pool.use(_.execute(resetCursorCmd)(name).void)

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
    pool.use { session =>
      session.execute(upsertCursorCmd)(name) *>
        session.unique(getCursorQ)(name)
    }

  private def advanceCursor(sequenceNumber: Long): IO[Unit] =
    pool.use(
      _.execute(advanceCursorCmd)(sequenceNumber *: name *: EmptyTuple).void
    )

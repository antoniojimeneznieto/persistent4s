package dcb.enrollment

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import dcb.Event as RawEvent
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*

/** Prepared statements and codecs for the enrollment view projections. */
object EnrollmentView:
  private val insertCourseSummary
      : Command[String *: String *: Int *: EmptyTuple] =
    sql"""INSERT INTO course_summary (course_id, course_name, max_capacity, enrolled_count)
          VALUES ($text, $text, $int4, 0)
          ON CONFLICT (course_id) DO NOTHING""".command

  private val insertStudentSummary: Command[String *: String *: EmptyTuple] =
    sql"""INSERT INTO student_summary (student_id, student_name, enrolled_count)
          VALUES ($text, $text, 0)
          ON CONFLICT (student_id) DO NOTHING""".command

  private val insertEnrollment
      : Command[OffsetDateTime *: String *: String *: EmptyTuple] =
    sql"""INSERT INTO course_enrollments (course_id, course_name, student_id, student_name, enrolled_at)
          SELECT cs.course_id, cs.course_name, ss.student_id, ss.student_name, $timestamptz
          FROM course_summary cs, student_summary ss
          WHERE cs.course_id = $text AND ss.student_id = $text
          ON CONFLICT (course_id, student_id) DO NOTHING""".command

  private val incCourseEnrolled: Command[String] =
    sql"UPDATE course_summary SET enrolled_count = enrolled_count + 1 WHERE course_id = $text".command

  private val incStudentEnrolled: Command[String] =
    sql"UPDATE student_summary SET enrolled_count = enrolled_count + 1 WHERE student_id = $text".command

  private val deleteEnrollment: Command[String *: String *: EmptyTuple] =
    sql"DELETE FROM course_enrollments WHERE course_id = $text AND student_id = $text".command

  private val decCourseEnrolled: Command[String] =
    sql"UPDATE course_summary SET enrolled_count = enrolled_count - 1 WHERE course_id = $text".command

  private val decStudentEnrolled: Command[String] =
    sql"UPDATE student_summary SET enrolled_count = enrolled_count - 1 WHERE student_id = $text".command

/** Projects events from the event log into the views database. Each event type
  * maps to SQL updates on the denormalized view tables.
  */
class EnrollmentView(pool: Resource[IO, Session[IO]]):
  import EnrollmentView.*

  def apply(event: RawEvent): IO[Unit] =
    decode(event) match
      case Some(e: CourseCreated)     => onCourseCreated(e)
      case Some(e: StudentRegistered) => onStudentRegistered(e)
      case Some(e: StudentEnrolled)   => onStudentEnrolled(e, event)
      case Some(e: StudentUnenrolled) => onStudentUnenrolled(e)
      case _                          => IO.unit

  private def onCourseCreated(e: CourseCreated): IO[Unit] =
    pool.use { session =>
      session
        .execute(insertCourseSummary)(
          e.courseId *: e.courseName *: e.maxCapacity *: EmptyTuple
        )
        .void
    }

  private def onStudentRegistered(e: StudentRegistered): IO[Unit] =
    pool.use { session =>
      session
        .execute(insertStudentSummary)(
          e.studentId *: e.studentName *: EmptyTuple
        )
        .void
    }

  private def onStudentEnrolled(e: StudentEnrolled, raw: RawEvent): IO[Unit] =
    pool.use { session =>
      session.transaction.use { _ =>
        val odt = raw.recordedAt.atOffset(ZoneOffset.UTC)
        for
          _ <- session.execute(insertEnrollment)(
            odt *: e.courseId *: e.studentId *: EmptyTuple
          )
          _ <- session.execute(incCourseEnrolled)(e.courseId)
          _ <- session.execute(incStudentEnrolled)(e.studentId)
        yield ()
      }
    }

  private def onStudentUnenrolled(e: StudentUnenrolled): IO[Unit] =
    pool.use { session =>
      session.transaction.use { _ =>
        for
          _ <- session.execute(deleteEnrollment)(
            e.courseId *: e.studentId *: EmptyTuple
          )
          _ <- session.execute(decCourseEnrolled)(e.courseId)
          _ <- session.execute(decStudentEnrolled)(e.studentId)
        yield ()
      }
    }

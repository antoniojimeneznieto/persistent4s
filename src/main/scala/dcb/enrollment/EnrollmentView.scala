package dcb.enrollment

import cats.effect.IO
import cats.syntax.all.*
import dcb.Event as RawEvent

/** Projects events from the event log into the views database. Each event type
  * maps to SQL updates on the denormalized view tables.
  */
class EnrollmentView(xa: Transactor[IO]):

  def apply(event: RawEvent): IO[Unit] =
    decode(event) match
      case Some(e: CourseCreated)     => onCourseCreated(e)
      case Some(e: StudentRegistered) => onStudentRegistered(e)
      case Some(e: StudentEnrolled)   => onStudentEnrolled(e, event)
      case Some(e: StudentUnenrolled) => onStudentUnenrolled(e)
      case _                          => IO.unit

  private def onCourseCreated(e: CourseCreated): IO[Unit] =
    sql"""INSERT INTO course_summary (course_id, course_name, max_capacity, enrolled_count)
          VALUES (${e.courseId}, ${e.courseName}, ${e.maxCapacity}, 0)
          ON CONFLICT (course_id) DO NOTHING""".update.run.transact(xa).void

  private def onStudentRegistered(e: StudentRegistered): IO[Unit] =
    sql"""INSERT INTO student_summary (student_id, student_name, enrolled_count)
          VALUES (${e.studentId}, ${e.studentName}, 0)
          ON CONFLICT (student_id) DO NOTHING""".update.run.transact(xa).void

  private def onStudentEnrolled(e: StudentEnrolled, raw: RawEvent): IO[Unit] =
    val program = for
      _ <-
        sql"""INSERT INTO course_enrollments (course_id, course_name, student_id, student_name, enrolled_at)
                 SELECT cs.course_id, cs.course_name, ss.student_id, ss.student_name, ${raw.recordedAt}
                 FROM course_summary cs, student_summary ss
                 WHERE cs.course_id = ${e.courseId} AND ss.student_id = ${e.studentId}
                 ON CONFLICT (course_id, student_id) DO NOTHING""".update.run
      _ <-
        sql"UPDATE course_summary SET enrolled_count = enrolled_count + 1 WHERE course_id = ${e.courseId}".update.run
      _ <-
        sql"UPDATE student_summary SET enrolled_count = enrolled_count + 1 WHERE student_id = ${e.studentId}".update.run
    yield ()
    program.transact(xa)

  private def onStudentUnenrolled(e: StudentUnenrolled): IO[Unit] =
    val program = for
      _ <-
        sql"DELETE FROM course_enrollments WHERE course_id = ${e.courseId} AND student_id = ${e.studentId}".update.run
      _ <-
        sql"UPDATE course_summary SET enrolled_count = enrolled_count - 1 WHERE course_id = ${e.courseId}".update.run
      _ <-
        sql"UPDATE student_summary SET enrolled_count = enrolled_count - 1 WHERE student_id = ${e.studentId}".update.run
    yield ()
    program.transact(xa)

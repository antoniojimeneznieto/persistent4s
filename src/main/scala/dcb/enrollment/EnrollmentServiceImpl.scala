package dcb.enrollment

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import dcb.api.*
import dcb.enrollment.*
import java.time.OffsetDateTime
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*

/** Prepared statements for view queries. */
object EnrollmentServiceImpl:
  private val courseSummaryQ: Query[String, GetCourseSummaryOutput] =
    sql"""SELECT course_id, course_name, max_capacity, enrolled_count
          FROM course_summary WHERE course_id = $text"""
      .query(text *: text *: int4 *: int4)
      .map { case id *: name *: max *: count *: EmptyTuple =>
        GetCourseSummaryOutput(id, name, max, count)
      }

  private val studentSummaryQ: Query[String, GetStudentSummaryOutput] =
    sql"""SELECT student_id, student_name, enrolled_count
          FROM student_summary WHERE student_id = $text"""
      .query(text *: text *: int4)
      .map { case id *: name *: count *: EmptyTuple =>
        GetStudentSummaryOutput(id, name, count)
      }

  private val courseEnrollmentsQ: Query[String, EnrollmentEntry] =
    sql"""SELECT student_id, student_name, enrolled_at
          FROM course_enrollments WHERE course_id = $text ORDER BY student_id"""
      .query(text *: text *: timestamptz)
      .map { case sid *: sname *: enrolledAt *: EmptyTuple =>
        EnrollmentEntry(sid, sname, enrolledAt.toInstant.toString)
      }

class EnrollmentServiceImpl(
    handler: EnrollmentCommandHandler,
    viewsPool: Resource[IO, Session[IO]]
) extends EnrollmentService[IO]:
  import EnrollmentServiceImpl.*

  override def createCourse(
      courseId: String,
      courseName: String,
      maxCapacity: Int
  ): IO[CreateCourseOutput] =
    handler
      .handle(CreateCourse(courseId, courseName, maxCapacity))
      .flatMap {
        case CmdSuccess(_) =>
          IO.pure(CreateCourseOutput(s"Course $courseId created"))
        case CmdRejected(reason) =>
          IO.raiseError(ConflictError(reason))
      }

  override def registerStudent(
      studentId: String,
      studentName: String
  ): IO[RegisterStudentOutput] =
    IO.println(s"Registering student $studentId: $studentName") *>
      handler
        .handle(RegisterStudent(studentId, studentName))
        .flatMap {
          case CmdSuccess(_) =>
            IO.pure(RegisterStudentOutput(s"Student $studentId registered"))
          case CmdRejected(reason) =>
            IO.println(s"Failed to register student $studentId: $reason") *>
              IO.raiseError(ConflictError(reason))
        }

  override def enrollStudent(
      courseId: String,
      studentId: String
  ): IO[EnrollStudentOutput] =
    handler
      .handle(EnrollStudent(courseId, studentId))
      .flatMap {
        case CmdSuccess(_) =>
          IO.pure(
            EnrollStudentOutput(s"Student $studentId enrolled in $courseId")
          )
        case CmdRejected(reason)
            if reason.contains("does not exist") || reason.contains(
              "not registered"
            ) =>
          IO.raiseError(NotFoundError(reason))
        case CmdRejected(reason) if reason.contains("full") =>
          IO.raiseError(CapacityError(reason))
        case CmdRejected(reason) =>
          IO.raiseError(ConflictError(reason))
      }

  override def unenrollStudent(
      courseId: String,
      studentId: String
  ): IO[UnenrollStudentOutput] =
    handler
      .handle(UnenrollStudent(courseId, studentId))
      .flatMap {
        case CmdSuccess(_) =>
          IO.pure(
            UnenrollStudentOutput(
              s"Student $studentId unenrolled from $courseId"
            )
          )
        case CmdRejected(reason) =>
          IO.raiseError(NotFoundError(reason))
      }

  override def getCourseSummary(courseId: String): IO[GetCourseSummaryOutput] =
    viewsPool.use { session =>
      session.option(courseSummaryQ)(courseId).flatMap {
        case Some(output) => IO.pure(output)
        case None => IO.raiseError(NotFoundError(s"Course $courseId not found"))
      }
    }

  override def getStudentSummary(
      studentId: String
  ): IO[GetStudentSummaryOutput] =
    viewsPool.use { session =>
      session.option(studentSummaryQ)(studentId).flatMap {
        case Some(output) => IO.pure(output)
        case None         =>
          IO.raiseError(NotFoundError(s"Student $studentId not found"))
      }
    }

  override def getCourseEnrollments(
      courseId: String
  ): IO[GetCourseEnrollmentsOutput] =
    viewsPool.use { session =>
      session.execute(courseEnrollmentsQ)(courseId).map { rows =>
        GetCourseEnrollmentsOutput(rows.toList)
      }
    }

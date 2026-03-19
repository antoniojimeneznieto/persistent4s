package dcb.enrollment

import cats.effect.IO
import cats.syntax.all.*
import dcb.api.*
import dcb.enrollment.*

class EnrollmentServiceImpl(
    handler: EnrollmentCommandHandler,
    viewsXa: Transactor[IO]
) extends EnrollmentService[IO]:

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
    sql"""SELECT course_id, course_name, max_capacity, enrolled_count
          FROM course_summary WHERE course_id = $courseId"""
      .query[(String, String, Int, Int)]
      .option
      .transact(viewsXa)
      .flatMap {
        case Some((id, name, max, count)) =>
          IO.pure(GetCourseSummaryOutput(id, name, max, count))
        case None =>
          IO.raiseError(NotFoundError(s"Course $courseId not found"))
      }

  override def getStudentSummary(
      studentId: String
  ): IO[GetStudentSummaryOutput] =
    sql"""SELECT student_id, student_name, enrolled_count
          FROM student_summary WHERE student_id = $studentId"""
      .query[(String, String, Int)]
      .option
      .transact(viewsXa)
      .flatMap {
        case Some((id, name, count)) =>
          IO.pure(GetStudentSummaryOutput(id, name, count))
        case None =>
          IO.raiseError(NotFoundError(s"Student $studentId not found"))
      }

  override def getCourseEnrollments(
      courseId: String
  ): IO[GetCourseEnrollmentsOutput] =
    sql"""SELECT student_id, student_name, enrolled_at
          FROM course_enrollments WHERE course_id = $courseId ORDER BY student_id"""
      .query[(String, String, java.sql.Timestamp)]
      .to[List]
      .transact(viewsXa)
      .map { rows =>
        GetCourseEnrollmentsOutput(
          rows.map { (sid, sname, enrolledAt) =>
            EnrollmentEntry(sid, sname, enrolledAt.toInstant.toString)
          }
        )
      }

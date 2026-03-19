package dcb.enrollment

import dcb.Tag
import io.circe.{Encoder, Decoder}

sealed trait Command

case class CreateCourse(courseId: String, courseName: String, maxCapacity: Int)
    extends Command

case class RegisterStudent(studentId: String, studentName: String)
    extends Command

case class EnrollStudent(courseId: String, studentId: String) extends Command

case class UnenrollStudent(courseId: String, studentId: String) extends Command

sealed trait Event

case class CourseCreated(courseId: String, courseName: String, maxCapacity: Int)
    extends Event derives Encoder.AsObject, Decoder
case class StudentRegistered(studentId: String, studentName: String)
    extends Event derives Encoder.AsObject, Decoder
case class StudentEnrolled(courseId: String, studentId: String) extends Event
    derives Encoder.AsObject,
      Decoder
case class StudentUnenrolled(courseId: String, studentId: String) extends Event
    derives Encoder.AsObject,
      Decoder

object EventTypes:
  val CourseCreated = "CourseCreated"
  val StudentRegistered = "StudentRegistered"
  val StudentEnrolled = "StudentEnrolled"
  val StudentUnenrolled = "StudentUnenrolled"

object Tags:
  def course(courseId: String): Tag = Tag(s"Course:$courseId")
  def student(studentId: String): Tag = Tag(s"Student:$studentId")

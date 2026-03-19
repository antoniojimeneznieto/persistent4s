package dcb.enrollment

import cats.effect.IO
import dcb.{Event, EventStore, NewEvent, Tag}
import io.circe.Encoder

// ── Command result ──────────────────────────────────────────────────

sealed trait CommandResult
case class CmdSuccess(events: List[Event]) extends CommandResult
case class CmdRejected(reason: String) extends CommandResult

// ── Command handler ─────────────────────────────────────────────────

class EnrollmentCommandHandler(
    eventStore: EventStore,
    maxCoursesPerStudent: Int = 5
):

  def handle(command: Command): IO[CommandResult] =
    command match
      case cmd: CreateCourse    => handleCreateCourse(cmd)
      case cmd: RegisterStudent => handleRegisterStudent(cmd)
      case cmd: EnrollStudent   => handleEnrollStudent(cmd)
      case cmd: UnenrollStudent => handleUnenrollStudent(cmd)

  private def handleCreateCourse(cmd: CreateCourse): IO[CommandResult] =
    val tags = Set(Tags.course(cmd.courseId))
    for
      stream <- eventStore.queryByTags(tags)
      course = CourseProjector(cmd.courseId).buildFrom(stream.events)
      result <-
        if course.exists then
          IO.pure(CmdRejected(s"Course ${cmd.courseId} already exists"))
        else
          val newEvent = NewEvent.from(
            CourseCreated(cmd.courseId, cmd.courseName, cmd.maxCapacity),
            tags
          )
          eventStore
            .append(List(newEvent), tags, stream.lastSequenceNumber)
            .map(CmdSuccess(_))
    yield result

  private def handleRegisterStudent(cmd: RegisterStudent): IO[CommandResult] =
    val tags = Set(Tags.student(cmd.studentId))
    for
      stream <- eventStore.queryByTags(tags)
      student = StudentProjector(cmd.studentId).buildFrom(stream.events)
      result <-
        if student.exists then
          IO.pure(CmdRejected(s"Student ${cmd.studentId} already registered"))
        else
          val newEvent = NewEvent.from(
            StudentRegistered(cmd.studentId, cmd.studentName),
            tags
          )
          eventStore
            .append(List(newEvent), tags, stream.lastSequenceNumber)
            .map(CmdSuccess(_))
    yield result

  private def handleEnrollStudent(cmd: EnrollStudent): IO[CommandResult] =
    val tags = Set(Tags.course(cmd.courseId), Tags.student(cmd.studentId))
    for
      stream <- eventStore.queryByTags(tags)
      course = CourseProjector(cmd.courseId).buildFrom(stream.events)
      student = StudentProjector(cmd.studentId).buildFrom(stream.events)
      enrollment = EnrollmentProjector(cmd.courseId, cmd.studentId).buildFrom(
        stream.events
      )
      result <-
        if !course.exists then
          IO.pure(CmdRejected(s"Course ${cmd.courseId} does not exist"))
        else if !student.exists then
          IO.pure(CmdRejected(s"Student ${cmd.studentId} is not registered"))
        else if enrollment.isEnrolled then
          IO.pure(
            CmdRejected(
              s"Student ${cmd.studentId} is already enrolled in course ${cmd.courseId}"
            )
          )
        else if course.enrolledCount >= course.maxCapacity then
          IO.pure(
            CmdRejected(
              s"Course ${cmd.courseId} is full (${course.maxCapacity} students)"
            )
          )
        else if student.enrolledCount >= maxCoursesPerStudent then
          IO.pure(
            CmdRejected(
              s"Student ${cmd.studentId} already enrolled in $maxCoursesPerStudent courses"
            )
          )
        else
          val newEvent = NewEvent.from(
            StudentEnrolled(cmd.courseId, cmd.studentId),
            tags
          )
          eventStore
            .append(List(newEvent), tags, stream.lastSequenceNumber)
            .map(CmdSuccess(_))
    yield result

  private def handleUnenrollStudent(cmd: UnenrollStudent): IO[CommandResult] =
    val tags = Set(Tags.course(cmd.courseId), Tags.student(cmd.studentId))
    for
      stream <- eventStore.queryByTags(tags)
      enrollment = EnrollmentProjector(cmd.courseId, cmd.studentId).buildFrom(
        stream.events
      )
      result <-
        if !enrollment.isEnrolled then
          IO.pure(
            CmdRejected(
              s"Student ${cmd.studentId} is not enrolled in course ${cmd.courseId}"
            )
          )
        else
          val newEvent = NewEvent.from(
            StudentUnenrolled(cmd.courseId, cmd.studentId),
            tags
          )
          eventStore
            .append(List(newEvent), tags, stream.lastSequenceNumber)
            .map(CmdSuccess(_))
    yield result

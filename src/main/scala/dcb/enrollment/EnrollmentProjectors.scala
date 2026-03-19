package dcb.enrollment

import dcb.Event as RawEvent

/** A decision projector extracts a focused state from an event stream. Each
  * projector only cares about its own concern — they are composed at the
  * command handler level, not merged into a single state.
  */
trait DecisionProjector[S]:
  def initial: S
  def evolve(state: S, event: RawEvent): S

  def buildFrom(events: List[RawEvent]): S =
    events.foldLeft(initial)(evolve)

/** Decode a raw store event into a typed domain event. */
def decode(raw: RawEvent): Option[Event] =
  raw.eventType match
    case EventTypes.CourseCreated     => raw.payload.as[CourseCreated].toOption
    case EventTypes.StudentRegistered =>
      raw.payload.as[StudentRegistered].toOption
    case EventTypes.StudentEnrolled => raw.payload.as[StudentEnrolled].toOption
    case EventTypes.StudentUnenrolled =>
      raw.payload.as[StudentUnenrolled].toOption
    case _ => None

// ── Course state ────────────────────────────────────────────────────

case class CourseState(
    exists: Boolean = false,
    maxCapacity: Int = 0,
    enrolledCount: Int = 0
)

class CourseProjector(courseId: String) extends DecisionProjector[CourseState]:
  val initial: CourseState = CourseState()

  def evolve(state: CourseState, event: RawEvent): CourseState =
    decode(event) match
      case Some(CourseCreated(`courseId`, _, maxCap)) =>
        state.copy(exists = true, maxCapacity = maxCap)
      case Some(StudentEnrolled(`courseId`, _)) =>
        state.copy(enrolledCount = state.enrolledCount + 1)
      case Some(StudentUnenrolled(`courseId`, _)) =>
        state.copy(enrolledCount = state.enrolledCount - 1)
      case _ => state

// ── Student state ───────────────────────────────────────────────────

case class StudentState(
    exists: Boolean = false,
    enrolledCount: Int = 0
)

class StudentProjector(studentId: String)
    extends DecisionProjector[StudentState]:
  val initial: StudentState = StudentState()

  def evolve(state: StudentState, event: RawEvent): StudentState =
    decode(event) match
      case Some(StudentRegistered(`studentId`, _)) =>
        state.copy(exists = true)
      case Some(StudentEnrolled(_, `studentId`)) =>
        state.copy(enrolledCount = state.enrolledCount + 1)
      case Some(StudentUnenrolled(_, `studentId`)) =>
        state.copy(enrolledCount = state.enrolledCount - 1)
      case _ => state

// ── Enrollment state (the pair) ─────────────────────────────────────

case class EnrollmentState(
    isEnrolled: Boolean = false
)

class EnrollmentProjector(courseId: String, studentId: String)
    extends DecisionProjector[EnrollmentState]:
  val initial: EnrollmentState = EnrollmentState()

  def evolve(state: EnrollmentState, event: RawEvent): EnrollmentState =
    decode(event) match
      case Some(StudentEnrolled(`courseId`, `studentId`)) =>
        state.copy(isEnrolled = true)
      case Some(StudentUnenrolled(`courseId`, `studentId`)) =>
        state.copy(isEnrolled = false)
      case _ => state

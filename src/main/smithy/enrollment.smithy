$version: "2"

namespace dcb.api

use alloy#simpleRestJson

@simpleRestJson
service EnrollmentService {
    version: "1.0.0"
    operations: [
        CreateCourse
        RegisterStudent
        EnrollStudent
        UnenrollStudent
        GetCourseSummary
        GetStudentSummary
        GetCourseEnrollments
    ]
}

// ── Commands ────────────────────────────────────────────────────────
@http(method: "POST", uri: "/courses", code: 201)
operation CreateCourse {
    input := {
        @required
        courseId: String

        @required
        courseName: String

        @required
        maxCapacity: Integer
    }

    output := {
        @required
        message: String
    }

    errors: [
        ConflictError
    ]
}

@http(method: "POST", uri: "/students", code: 201)
operation RegisterStudent {
    input := {
        @required
        studentId: String

        @required
        studentName: String
    }

    output := {
        @required
        message: String
    }

    errors: [
        ConflictError
    ]
}

@http(method: "POST", uri: "/courses/{courseId}/enroll", code: 200)
operation EnrollStudent {
    input := {
        @required
        @httpLabel
        courseId: String

        @required
        studentId: String
    }

    output := {
        @required
        message: String
    }

    errors: [
        ConflictError
        NotFoundError
        CapacityError
    ]
}

@http(method: "POST", uri: "/courses/{courseId}/unenroll", code: 200)
operation UnenrollStudent {
    input := {
        @required
        @httpLabel
        courseId: String

        @required
        studentId: String
    }

    output := {
        @required
        message: String
    }

    errors: [
        NotFoundError
    ]
}

// ── Queries ─────────────────────────────────────────────────────────
@readonly
@http(method: "GET", uri: "/courses/{courseId}", code: 200)
operation GetCourseSummary {
    input := {
        @required
        @httpLabel
        courseId: String
    }

    output := {
        @required
        courseId: String

        @required
        courseName: String

        @required
        maxCapacity: Integer

        @required
        enrolledCount: Integer
    }

    errors: [
        NotFoundError
    ]
}

@readonly
@http(method: "GET", uri: "/students/{studentId}", code: 200)
operation GetStudentSummary {
    input := {
        @required
        @httpLabel
        studentId: String
    }

    output := {
        @required
        studentId: String

        @required
        studentName: String

        @required
        enrolledCount: Integer
    }

    errors: [
        NotFoundError
    ]
}

@readonly
@http(method: "GET", uri: "/courses/{courseId}/enrollments", code: 200)
operation GetCourseEnrollments {
    input := {
        @required
        @httpLabel
        courseId: String
    }

    output := {
        @required
        enrollments: EnrollmentList
    }
}

list EnrollmentList {
    member: EnrollmentEntry
}

structure EnrollmentEntry {
    @required
    studentId: String

    @required
    studentName: String

    @required
    enrolledAt: String
}

// ── Errors ──────────────────────────────────────────────────────────
@error("client")
@httpError(409)
structure ConflictError {
    @required
    message: String
}

@error("client")
@httpError(404)
structure NotFoundError {
    @required
    message: String
}

@error("client")
@httpError(422)
structure CapacityError {
    @required
    message: String
}

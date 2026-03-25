$version: "2"

namespace persistent4s.examples.school.api

use alloy#simpleRestJson

@simpleRestJson
service EnrollmentService {
    operations: [EnrollStudent]
}

@http(method: "POST", uri: "/enrollments")
@idempotent
operation EnrollStudent {
    input := {
        @required
        studentId: String
        @required
        courseId: String
    }
}

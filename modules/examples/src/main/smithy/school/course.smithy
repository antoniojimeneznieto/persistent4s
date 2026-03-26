$version: "2"

namespace persistent4s.examples.school.api

use alloy#simpleRestJson

@simpleRestJson
service CourseService {
    operations: [CreateCourse]
}

@http(method: "POST", uri: "/courses")
@idempotent
operation CreateCourse {
    input := {
        @required
        title: String
        @required
        capacity: Integer
    }
    output := {
        @required
        courseId: String
    }
}

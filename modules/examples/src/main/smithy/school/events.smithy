$version: "2"

namespace persistent4s.examples.school.api

use alloy#simpleRestJson

@simpleRestJson
service EventsService {
    operations: [GetEvents]
}

@http(method: "GET", uri: "/events")
@readonly
operation GetEvents {
    output := {
        @required
        events: EventList
    }
}

list EventList {
    member: Event
}

structure Event {
    @required
    globalPosition: Long
    @required
    tags: TagList
    @required
    eventType: String
    @required
    timestamp: String
    @required
    payload: String
}

list TagList {
    member: String
}

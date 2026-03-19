CREATE DATABASE views;
\connect views


CREATE TABLE course_enrollments (
    course_id    TEXT NOT NULL,
    course_name  TEXT NOT NULL,
    student_id   TEXT NOT NULL,
    student_name TEXT NOT NULL,
    enrolled_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (course_id, student_id)
);

CREATE TABLE course_summary (
    course_id       TEXT PRIMARY KEY,
    course_name     TEXT NOT NULL,
    max_capacity    INT NOT NULL,
    enrolled_count  INT NOT NULL DEFAULT 0
);

CREATE TABLE student_summary (
    student_id      TEXT PRIMARY KEY,
    student_name    TEXT NOT NULL,
    enrolled_count  INT NOT NULL DEFAULT 0,
    max_courses     INT NOT NULL DEFAULT 5
);

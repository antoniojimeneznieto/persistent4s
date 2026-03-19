import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import dcb.{EventStore, ProjectionWorker}
import dcb.api.EnrollmentService
import dcb.enrollment.*
import dcb.config.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.typelevel.ci.CIString
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.http4s.swagger.docs
import pureconfig.ConfigSource
import skunk.Session
import natchez.Trace.Implicits.noop

object Application extends IOApp.Simple:

  /*   private val failureLogHandler: LogHandler[IO] = new LogHandler[IO]:
    def run(event: doobie.util.log.LogEvent): IO[Unit] = event match
      case ExecFailure(sql, params, label, exec, failure) =>
        IO.println(
          s"""[SQL ExecFailure] $label
             |  SQL: ${sql.linesIterator
              .dropWhile(_.trim.isEmpty)
              .mkString("\n       ")}
             |  Params: ${params.allParams
              .map(_.mkString("(", ", ", ")"))
              .mkString("[", ", ", "]")}
             |  Elapsed: ${exec.toMillis} ms
             |  Error: ${failure.getMessage}""".stripMargin
        )
      case ProcessingFailure(sql, params, label, exec, processing, failure) =>
        IO.println(
          s"""[SQL ProcessingFailure] $label
             |  SQL: ${sql.linesIterator
              .dropWhile(_.trim.isEmpty)
              .mkString("\n       ")}
             |  Params: ${params.allParams
              .map(_.mkString("(", ", ", ")"))
              .mkString("[", ", ", "]")}
             |  Elapsed: ${exec.toMillis} ms exec + ${processing.toMillis} ms processing
             |  Error: ${failure.getMessage}""".stripMargin
        )
      case Success(_, _, _, _, _) => IO.unit */

  private def sessionPoolRessource(
      postgresConfig: DbConfig
  ): Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host = postgresConfig.host,
      port = postgresConfig.port,
      user = postgresConfig.user,
      password = Some(postgresConfig.password),
      database = postgresConfig.database,
      max = postgresConfig.max
    )

  private def withErrorLogging(app: HttpApp[IO]): HttpApp[IO] =
    HttpApp[IO] { request =>
      app.run(request).handleErrorWith { error =>
        IO.println(
          s"[ERROR] ${request.method} ${request.uri} — ${error.getClass.getSimpleName}: ${error.getMessage}"
        ) *>
          IO.blocking(error.printStackTrace()) *>
          IO.pure(
            Response[IO](org.http4s.Status.InternalServerError)
              .withEntity(s"""{"message":"${error.getMessage}"}""")
              .withHeaders(
                org.http4s.Header
                  .Raw(CIString("Content-Type"), "application/json")
              )
          )
      }
    }

  override def run: IO[Unit] =
    ConfigSource.default.at("db").load[AppConfig] match
      case Left(errors) =>
        IO.println(s"Failed to load configuration: ${errors.prettyPrint}") *>
          IO.raiseError(new RuntimeException("Invalid configuration"))
      case Right(AppConfig(eventLogConfig, viewsConfig)) =>
        val dbResource = for
          eventLogXa <- sessionPoolRessource(eventLogConfig)
          viewsXa <- sessionPoolRessource(viewsConfig)
        yield (eventLogXa, viewsXa)

        dbResource
          .use { case (eventLogXa, viewsXa) =>

            val eventStore = EventStore(eventLogXa)
            val enrollmentView = EnrollmentView(viewsXa)
            val handler = EnrollmentCommandHandler(eventStore)
            val service = EnrollmentServiceImpl(handler, viewsXa)

            val worker = ProjectionWorker(
              name = "enrollment-views",
              eventStore = eventStore,
              eventLogXa = eventLogXa,
              project = enrollmentView(_)
            )

            val routes: Resource[IO, HttpRoutes[IO]] =
              SimpleRestJsonBuilder.routes(service).resource

            val docRoutes: HttpRoutes[IO] =
              docs[IO](EnrollmentService)

            routes.flatMap { apiRoutes =>
              val httpApp =
                withErrorLogging((apiRoutes <+> docRoutes).orNotFound)
              EmberServerBuilder
                .default[IO]
                .withHost(host"0.0.0.0")
                .withPort(port"8080")
                .withHttpApp(httpApp)
                .build
                .map(server => (server, worker))
            }
          }
          .use { (server, worker) =>
            IO.println(s"Server started at http://localhost:8080") *>
              IO.println("Swagger docs at http://localhost:8080/docs") *>
              IO.println("Projection worker started") *>
              worker.run.both(IO.never).void
          }

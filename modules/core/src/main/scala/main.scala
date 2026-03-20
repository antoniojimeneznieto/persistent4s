package core

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:

  override def run: IO[Unit] = IO.println("Hello, World!")

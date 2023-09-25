package win.amuxix.customraces

import scala.compiletime.uninitialized
import java.util.logging.Logger

import cats.effect.IO

object Logger:
  var logger: Logger = uninitialized

  def info(text: Any): IO[Unit] = IO(logger.info(text.toString))

  def trace(text: Any): IO[Unit] = IO(logger.finest(text.toString))

  def warn(text: Any): IO[Unit] = IO(logger.warning(text.toString))

  def severe(text: Any): IO[Unit] = IO(logger.severe(text.toString))

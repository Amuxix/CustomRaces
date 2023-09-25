package win.amuxix.customraces

import cats.effect.IO
import fs2.{text, Stream}
import fs2.io.file.{Files, Path}
import io.circe.{Decoder, Encoder}
import io.circe.Error
import io.circe.parser.decode
import io.circe.syntax.*

object Persistable:
  private val dataPath = Path.fromNioPath(CustomRaces.self.getDataFolder.toPath)

  private def createFolderIfNotExists(path: Path): Stream[IO, Unit] = Stream.eval(Files[IO].exists(path).flatMap {
    case true  => IO.unit
    case false => Files[IO].createDirectory(path)
  })

  extension (path: Path)
    def strippedName: String =
      val fn = path.fileName.toString
      val i  = fn.lastIndexOf('.')
      if i == 0 | i == -1 then fn else fn.substring(0, i)

  private def loadFile[A: Decoder, B](path: Path, errorHandler: (String, Error) => IO[Unit], f: (A, Path) => B): Stream[IO, B] =
    for
      contents <- Files[IO].readAll(path).through(text.utf8.decode)
      thing    <- decode[A](contents)
                    .fold(
                      error => Stream.eval(errorHandler(path.fileName.toString, error).as(None)),
                      thing => Stream.emit(Some(f(thing, path))),
                    )
                    .collect { case Some(thing) =>
                      thing
                    }
    yield thing

  def load[A: Decoder, B](
    folder: String,
    fileName: String,
    errorHandler: (String, Error) => IO[Unit],
    f: (A, Path) => B = (a: A, path: Path) => a,
  ): IO[Option[B]] =
    val filePath              = dataPath / folder / fileName
    val stream: Stream[IO, B] = for
      _      <- createFolderIfNotExists(dataPath)
      _      <- createFolderIfNotExists(dataPath / folder)
      exists <- Stream.eval(Files[IO].exists(filePath))
      thing  <- if !exists then Stream.empty else loadFile(filePath, errorHandler, f)
    yield thing
    stream.compile.toList.map(_.headOption)

  def loadAll[A: Decoder, B](folder: String, errorHandler: (String, Error) => IO[Unit], f: (A, Path) => B = (a: A, path: Path) => a): IO[List[B]] =
    val folderPath            = dataPath / folder
    val stream: Stream[IO, B] = for
      _     <- createFolderIfNotExists(dataPath)
      _     <- createFolderIfNotExists(folderPath)
      path  <- Files[IO].list(folderPath).filter(_.extName.equalsIgnoreCase(".json"))
      thing <- loadFile(path, errorHandler, f)
    yield thing
    stream.compile.toList

  private def saveFile[A: Encoder](thingPath: Path, thing: A): Stream[IO, Unit] =
    for
      exists <- Stream.eval(Files[IO].exists(thingPath))
      _      <- if exists then Stream.eval(IO.unit) else Stream.eval(Files[IO].createFile(thingPath))
      _      <- Stream.emit(thing.asJson /*.deepDropNullValues*/ .spaces2).through(text.utf8.encode).through(Files[IO].writeAll(thingPath))
    yield ()

  def save[A: Encoder](folder: String, fileName: String, thing: A): IO[Unit] =
    val filePath                 = dataPath / folder / fileName
    val stream: Stream[IO, Unit] = for
      _ <- createFolderIfNotExists(dataPath)
      _ <- createFolderIfNotExists(dataPath / folder)
      _ <- saveFile(filePath, thing)
    yield ()
    stream.compile.drain

  def saveAll[A: Encoder](folder: String, things: List[A], name: A => String): IO[Unit] =
    val folderPath               = dataPath / folder
    val stream: Stream[IO, Unit] = for
      _        <- createFolderIfNotExists(dataPath)
      _        <- createFolderIfNotExists(folderPath)
      thing    <- Stream.emits(things)
      thingPath = folderPath / name(thing)
      _        <- saveFile(thingPath, thing)
    yield ()
    stream.compile.drain

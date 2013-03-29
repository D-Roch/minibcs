package gd.eval

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Files

trait SandboxedLanguage {
  private val stdout = new StringBuilder
  private val stderr = new StringBuilder

  private val logger = ProcessLogger(
    out => stdout.append(out),
    err => stderr.append(err))

  /** Other binaries that should be on the system to use this language. */
  val extraRequiredBinaries: Option[List[String]] = None

  /** Extension of the language we're evaluating, without period. */
  val extension: String

  /** Source code of the program being evaluated. */
  val code: String

  /** After how long, in seconds, should we kill the evaluation? */
  val timeout: Int = 5

  /** A home directory that is unique to this evaluation. */
  val home = Files.createTempDirectory("eval-").toFile

  /** A /tmp mount for the sandbox (also available in its ~/.tmp). */
  val tmp = new File(s"${home}/.tmp")

  /** The code's filename. */
  lazy val filename = s"${home.getName}.${extension}"

  /** How do we compile the code, if we need to? */
  val compileCommand: Option[Seq[String]] = None

  /** How do we run the code? */
  val command: Seq[String]

  /** How do we sandbox the eval? */
  private val sandboxCommand: Seq[String] = Seq(
    "timeout", timeout.toString, "sandbox", "-H", home.toString, "-T",
    tmp.toString, "-t", "sandbox_x_t", "timeout", timeout.toString)

  private def writeCodeToFile() {
    tmp.mkdirs()
    val output = new BufferedWriter(new FileWriter(new File(s"${home}/${filename}")))
    output.write(code)
    output.flush
  }

  /** Return a Boolean indicating whether or not SELinux is enforcing. */
  private def isSELinuxEnforcing() = "getenforce".!!.trim == "Enforcing"

  /** The result of an evaluation. */
  case class Result(
    stdout: String,
    stderr: String,
    wallTime: Long,
    exitCode: Int,
    compilationResult: Option[Result] = None
  )

  /** Run a command in the Sandbox.
    *
    * @return A [[Result]] with the result.
    */
  private def runInSandbox(
    command: Seq[String],
    compilationResult: Option[Result] = None) = {
      val stdout = new StringBuilder
      val stderr = new StringBuilder
      val logger = ProcessLogger(
        out => stdout.append(out),
        err => stderr.append(err))
      val startTime = System.currentTimeMillis
      val exitCode = (sandboxCommand ++ command) ! logger
      val wallTime = System.currentTimeMillis - startTime
      Result(
        stdout.toString,
        stderr.toString,
        wallTime,
        exitCode,
        compilationResult)
  }


  /** Evaluate the code.
    *
    * @return a Left[Throwable] if we hit an internal issue, such as SELinux being altered.
    *         Otherwise, a Right[Future[SandboxedLanguage]].
    */
  def evaluate() = {
    if (isSELinuxEnforcing()) {
      writeCodeToFile()

      val compilationResult = compileCommand match {
        case Some(command) => Some(runInSandbox(command))
        case _ => None
      }

      val result = runInSandbox(command, compilationResult)

      Right(result)
    } else {
      Left(new SecurityException("SELinux is not enforcing. Bailing out early."))
    }
  }
}
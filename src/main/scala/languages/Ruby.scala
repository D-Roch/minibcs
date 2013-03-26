package gd.eval.languages

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Ruby(code: String) extends SandboxedLanguage {
  val extension = "rb"
  val command = Seq("ruby", file.getAbsolutePath)
}

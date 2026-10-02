package kreuzberg.i18n

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Checks `tr("key", ...)` calls in Scala sources against `.rctr` translation catalogs.
 *
 * Usage (e.g. via `runMain` from sbt):
 * {{{
 * kreuzberg.i18n.TranslationCheck --sources <dir>... --catalogs <file|dir>... [--strict]
 * }}}
 *
 * Exits with 1 on errors (and on warnings with `--strict`).
 */
object TranslationCheck {

  /** Result of a check. */
  case class Report(errors: Seq[String], warnings: Seq[String], infos: Seq[String]) {
    def failed(strict: Boolean): Boolean = errors.nonEmpty || (strict && warnings.nonEmpty)

    def lines: Seq[String] = {
      errors.map("[error] " + _) ++ warnings.map("[warn] " + _) ++ infos.map("[info] " + _)
    }
  }

  def main(args: Array[String]): Unit = {
    val options = parseArgs(args.toList)
    val report  = run(options.sources, options.catalogs)
    report.lines.foreach(println)
    if (report.failed(options.strict)) {
      println(s"Translation check failed (${report.errors.size} errors, ${report.warnings.size} warnings)")
      sys.exit(1)
    } else {
      println(s"Translation check passed (${report.warnings.size} warnings)")
    }
  }

  /** Checks Scala files below `sources` against catalogs (files or directories containing `.rctr` files). */
  def run(sources: Seq[Path], catalogs: Seq[Path]): Report = {
    val calls       = listFiles(sources, ".scala").flatMap { file =>
      TrCallScanner.scan(read(file), file.toString)
    }
    val parsed      = listFiles(catalogs, ".rctr").map { file =>
      CatalogParser.parse(read(file), file.toString)
    }
    val parseErrors = parsed.collect { case Left(error) => error.toString }
    if (parseErrors.nonEmpty) {
      Report(parseErrors, Nil, Nil)
    } else {
      check(calls, parsed.collect { case Right(catalog) => catalog })
    }
  }

  /** Checks calls against parsed catalogs. */
  def check(calls: Seq[TrCall], catalogs: Seq[Catalog]): Report = {
    CatalogSet(catalogs, defaultLocale = Catalog.DefaultLocale) match {
      case Left(error) => Report(Seq(error), Nil, Nil)
      case Right(set)  => checkSet(calls, set)
    }
  }

  private def checkSet(calls: Seq[TrCall], set: CatalogSet): Report = {
    val default = set.default.entries

    val callErrors = calls.flatMap { call =>
      default.get(call.key) match {
        case None           => Some(s"${call.position}: Unknown key ${call.key}")
        case Some(template) =>
          val expected = Placeholders.arity(template)
          Option.when(expected != call.argCount) {
            s"${call.position}: ${call.key} expects ${expected} arguments, got ${call.argCount}"
          }
      }
    }

    val translations = set.catalogs.filterNot(_.isDefault).sortBy(_.locale)

    val missing = translations.flatMap { catalog =>
      val keys = default.keySet.diff(catalog.entries.keySet).toSeq.sorted
      Option.when(keys.nonEmpty) {
        s"${catalog.locale}: ${keys.size} of ${default.size} keys untranslated (${abbreviate(keys)})"
      }
    }

    val orphans = translations.flatMap { catalog =>
      val keys = catalog.entries.keySet.diff(default.keySet).toSeq.sorted
      Option.when(keys.nonEmpty) {
        s"${catalog.locale}: ${keys.size} keys not in default catalog (${abbreviate(keys)})"
      }
    }

    val placeholderMismatches = for {
      catalog         <- translations
      (key, message)  <- catalog.entries.toSeq.sortBy(_._1)
      defaultTemplate <- default.get(key)
      if Placeholders(message) != Placeholders(defaultTemplate)
    } yield s"${catalog.source}: Placeholders of ${key} differ from default catalog"

    val usedKeys = calls.map(_.key).toSet
    val unused   = default.keySet.diff(usedKeys).toSeq.sorted
    val infos    = Option.when(unused.nonEmpty)(s"${unused.size} keys not referenced by tr(...): ${abbreviate(unused)}")

    Report(callErrors, missing ++ orphans ++ placeholderMismatches, infos.toSeq)
  }

  private def abbreviate(keys: Seq[String], max: Int = 10): String = {
    if (keys.size <= max) keys.mkString(", ") else keys.take(max).mkString(", ") + ", ..."
  }

  private def listFiles(roots: Seq[Path], suffix: String): Seq[Path] = {
    roots
      .flatMap { root =>
        if (Files.isDirectory(root)) {
          Using.resource(Files.walk(root)) { stream =>
            stream
              .iterator()
              .asScala
              .filter { p =>
                Files.isRegularFile(p) && p.toString
                  .endsWith(suffix) && !root.relativize(p).iterator().asScala.exists(_.toString == "target")
              }
              .toList
          }
        } else if (Files.isRegularFile(root)) {
          Seq(root)
        } else {
          throw new IllegalArgumentException(s"Not found: ${root}")
        }
      }
      .sortBy(_.toString)
  }

  private def read(path: Path): String = {
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }

  private case class Options(sources: Seq[Path] = Nil, catalogs: Seq[Path] = Nil, strict: Boolean = false)

  private def parseArgs(args: List[String]): Options = {
    def loop(rest: List[String], mode: Option[String], acc: Options): Options = {
      rest match {
        case Nil                  => acc
        case "--strict" :: tail   => loop(tail, None, acc.copy(strict = true))
        case "--sources" :: tail  => loop(tail, Some("sources"), acc)
        case "--catalogs" :: tail => loop(tail, Some("catalogs"), acc)
        case value :: tail        =>
          mode match {
            case Some("sources")  => loop(tail, mode, acc.copy(sources = acc.sources :+ Paths.get(value)))
            case Some("catalogs") => loop(tail, mode, acc.copy(catalogs = acc.catalogs :+ Paths.get(value)))
            case _                => throw new IllegalArgumentException(s"Unexpected argument: ${value}")
          }
      }
    }
    loop(args, None, Options())
  }
}

package org.specs2
package runner

import main.{ArgumentsArgs, Arguments}
import control._
import specification.core._
import runner.Runner._
import scalaz.std.anyVal._
import scalaz.syntax.bind._
import scalaz.syntax.traverse._
import scalaz.std.list._
import SpecificationsFinder._

/**
 * This trait finds specifications in the source directory, instantiate them
 * and report them using various printers as specified on the command line
 *
 */
trait FilesRunner {

  def run(args: Array[String]) {
    val env = Env(arguments = Arguments(args.mkString(" ")))

    val actions: Action[Unit] = run(env) >> Actions.safe(env.shutdown)
    execute(actions)
  }

  def run(env: Env): Action[Unit] = {
    val args = env.arguments

    val specs = findSpecifications(
      path     = args.commandLine.value("filesrunner.path").getOrElse("**/*.scala"),
      pattern  = args.commandLine.value("filesrunner.pattern").getOrElse(".*Spec"),
      basePath = args.commandLine.value("filesrunner.basepath").getOrElse(new java.io.File("src/test/scala").getAbsolutePath),
      verbose  = isVerbose(args))

    for {
      _  <- beforeExecution(args, isVerbose(args))
      ss <- specs.map(sort(env))
      _  <- ss.toList.map(ClassRunner.report(env)).sequenceU
      _  <- afterExecution(ss, isVerbose(args))
    } yield ()
  }

  /** sort the specifications in topological order where specification i doesn't depend on specification j if i < j */
  def sort(env: Env) = { specifications: Seq[SpecificationStructure] =>
    SpecificationStructure.topologicalSort(env)(specifications).getOrElse(specifications)
  }

  /** @return true if the output must be verbose for debugging */
  def isVerbose(args: Arguments) = args.commandLine.contains("filesrunner.verbose")

  /** print a message before the execution */
  protected def beforeExecution(args: Arguments, verbose: Boolean): Action[Unit] = for {
    _        <- log("\nExecuting specifications", verbose)
    printers <- ClassRunner.createPrinters(args, Thread.currentThread.getContextClassLoader)
    _        <- log("printers are "+printers.mkString(", "), verbose)
  } yield ()


  /** print a message after the execution based on the number of specifications */
  protected def afterExecution(specs: Seq[SpecificationStructure], verbose: Boolean): Action[Unit] = {
    if (specs.isEmpty) log("No specification found\n", verbose)
    else               log("Finished the execution of "+specs.size+" specifications\n", verbose)
  }
}

object filesRunner extends FilesRunner {
  def main(args: Array[String]) = run(args)
}
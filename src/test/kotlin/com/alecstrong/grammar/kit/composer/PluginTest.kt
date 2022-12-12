package com.alecstrong.grammar.kit.composer

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTest {
  private val String.fixtureDir: File get() = File("src/test/fixtures/$this")

  private fun testing(path: String, vararg tasks: String, clean: Boolean = true): Pair<BuildResult, BuildResult> {
    val fixtureDir = path.fixtureDir
    val gradleRoot = File(fixtureDir, "gradle").also { it.mkdir() }
    File("gradle/wrapper").copyRecursively(File(gradleRoot, "wrapper"), true)

    val runner = GradleRunner.create()
      .withProjectDir(fixtureDir)
      .withPluginClasspath()
      .forwardOutput()

    if (clean) {
      runner.withArguments("clean").build()
    }

    val firstRun = runner.withArguments(
      *tasks,
      "--stacktrace",
      "--configuration-cache",
    ).build()

    for (task in tasks) {
      assertEquals(TaskOutcome.SUCCESS, firstRun.task(task)?.outcome)
    }

    val secondCachedRun = runner.withArguments(
      *tasks,
      "--stacktrace",
      "--configuration-cache",
    ).build()

    for (task in tasks) {
      assertEquals(TaskOutcome.UP_TO_DATE, secondCachedRun.task(task)?.outcome)
    }

    return firstRun to secondCachedRun
  }

  @Test fun `unchanged grammar files are up to date`() {
    val fixture = "multiple-bnf-files"
    testing(fixture, ":assemble")
    val bnfFile = File(fixture.fixtureDir, "src/main/kotlin/com/example/bar.bnf")
    val originalBar = bnfFile.copyTo(File(bnfFile.parent, "${bnfFile.nameWithoutExtension}.oldbnf"), overwrite = true)

    bnfFile.appendText("testingIndependentChanges ::= 'SUCCESS'\n")

    val (firstRun) = testing(fixture, ":assemble", clean = false)
    originalBar.copyTo(bnfFile, overwrite = true)
    originalBar.delete()

    assertEquals(TaskOutcome.SUCCESS, firstRun.task(":createComposablecom_example_barGrammar")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generatecom_example_barParser")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, firstRun.task(":createComposablecom_example_fooGrammar")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, firstRun.task(":generatecom_example_fooParser")?.outcome)
  }

  @Test fun composing() {
    testing("composing", ":assemble")
  }

  @Test fun applying() {
    testing("applying", ":assemble")
  }
}

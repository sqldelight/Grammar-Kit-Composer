package com.alecstrong.grammar.kit.composer

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTest {
  private fun testing(path: String, vararg tasks: String) {
    val fixtureDir = File("src/test/fixtures/$path")
    val gradleRoot = File(fixtureDir, "gradle").also { it.mkdir() }
    File("gradle/wrapper").copyRecursively(File(gradleRoot, "wrapper"), true)

    val runner = GradleRunner.create()
      .withProjectDir(fixtureDir)
      .withPluginClasspath()
      .withDebug(true)
      .forwardOutput()

    val firstCleanRun = runner.withArguments(
      "clean",
      *tasks,
      "--stacktrace",
      // "--configuration-cache",
      ).build()

    for (task in tasks) {
      assertEquals(TaskOutcome.SUCCESS, firstCleanRun.task(task)?.outcome)
    }
/*
val secondCachedRun = runner.withArguments(
      *tasks,
      "--stacktrace",
      // "--configuration-cache",
      ).build()

      for (task in tasks) {
        assertEquals(TaskOutcome.FROM_CACHE, secondCachedRun.task(task)?.outcome)
      }
    }
 */
  }

  @Test fun `non-composing`() = testing("non-composing", ":assemble")

  @Test fun composing() = testing("composing", ":assemble")

  @Test fun applying() = testing("applying", ":assemble")
}

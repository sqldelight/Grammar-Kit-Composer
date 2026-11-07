package com.alecstrong.grammar.kit.composer

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.Directory
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask

open class GrammarKitComposerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("org.jetbrains.intellij.platform.grammarkit")

    val extension =
      project.extensions.create("grammarKitComposer", GrammarKitComposerExtension::class.java)
    //  declare the two IntelliJ URLs in this plugin instead of forcing the consumer plugins to
    // declare
    if (project.settingsRepositoriesMode() != RepositoriesMode.FAIL_ON_PROJECT_REPOS) {
      INTELLIJ_REPOSITORIES.forEach { url -> project.repositories.maven { it.setUrl(url) } }
    }

    val grammar =
      project.configurations.register("grammar") { configuration ->
        configuration.isCanBeResolved = true
        configuration.isCanBeConsumed = false
        // excludes because https://youtrack.jetbrains.com/issue/IDEA-301677
        configuration.exclude(mapOf("group" to "com.jetbrains.rd"))
        configuration.exclude(mapOf("group" to "org.jetbrains.marketplace"))
        configuration.exclude(mapOf("group" to "org.roaringbitmap"))
        configuration.exclude(mapOf("group" to "org.jetbrains.plugins"))
        configuration.exclude(mapOf("module" to "idea"))
        configuration.exclude(mapOf("module" to "ant"))
        configuration.defaultDependencies { dependencies ->
          extension.intellijRelease.orNull?.let { intellijRelease ->
            dependencies.addAll(project.platformDependencies(intellijRelease))
          }
          dependencies.add(
            project.dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
          )
        }
      }

    project.configurations.named("intellijPlatformGrammarKit") { configuration ->
      configuration.dependencies.addAllLater(
        extension.grammarKitRelease
          .map { listOf(project.dependencies.create("org.jetbrains:grammar-kit:$it")) }
          .orElse(emptyList())
      )
    }

    val rootDir =
      project.layout.projectDirectory.dir("src${File.separatorChar}main${File.separatorChar}kotlin")
    rootDir.forBnfFiles { bnfFile ->
      val name =
        bnfFile.toRelativeString(rootDir.asFile).replace(File.separatorChar, '_').dropLast(4)
      val outputDirectory = project.layout.buildDirectory.dir("grammars${File.separatorChar}$name")

      val compose =
        project.tasks.register("createComposable${name}Grammar", BnfExtenderTask::class.java) {
          it.bnfFile.set(bnfFile)
          it.root.set(rootDir.asFile.absolutePath)
          it.outputDirectory.set(outputDirectory)
          it.group = "grammar"
          it.description = "Generate composable grammars from .bnf files."
        }

      val gen =
        project.tasks.register("generate${name}Parser", GenerateParserTask::class.java) {
          generateParserTask ->
          val outputs =
            getOutputs(
              bnf = bnfFile,
              outputDirectory = outputDirectory,
              root = rootDir.asFile.absolutePath,
            )

          generateParserTask.dependsOn(compose)
          generateParserTask.sourceFile.set(outputs.outputFile)
          generateParserTask.targetRootOutputDir.set(outputDirectory)
          generateParserTask.pathToParser.set(outputs.parserClassString)
          generateParserTask.pathToPsiRoot.set(outputs.psiPackage)
          generateParserTask.purgeOldFiles.set(true)
          generateParserTask.group = "grammar"

          generateParserTask.classpath(grammar)
          // Move this dummy "idea.config.path" into the plugin, currently set in both SqlDelight
          // and Sql-Psi
          generateParserTask.systemProperty("idea.config.path", "some/non/existent/path")
        }

      project.pluginManager.withPlugin("org.gradle.java") {
        (project.extensions.getByName("sourceSets") as SourceSetContainer)
          .getByName("main")
          .java
          .srcDir(
            gen.flatMap { it.targetRootOutputDir }
          ) // targetRootOutputDir allowed as dependsOn is used
      }

      project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        project.tasks.named("compileKotlin").configure { it.dependsOn(gen) }
      }

      project.pluginManager.withPlugin("com.google.devtools.ksp") {
        project.afterEvaluate { project.tasks.named("kspKotlin").configure { it.dependsOn(gen) } }
      }

      project.tasks.configureEach {
        if (
          it.name.contains("dokka") ||
            it.name == "sourcesJar" ||
            it.name == "kotlinSourcesJar" ||
            it.name == "javaSourcesJar"
        ) {
          it.dependsOn(gen, compose)
        }
      }
    }
  }

  private fun Project.platformDependencies(intellijRelease: String): List<Dependency> =
    listOf(
        "com.jetbrains.intellij.platform:indexing-impl:$intellijRelease",
        "com.jetbrains.intellij.platform:analysis-impl:$intellijRelease",
        "com.jetbrains.intellij.platform:core-impl:$intellijRelease",
        "com.jetbrains.intellij.platform:lang-impl:$intellijRelease",
        "org.jetbrains.intellij.deps:asm-all:7.0.1",
      )
      .map(dependencies::create)

  // https://github.com/gradle/gradle/issues/17295#issuecomment-1053620508
  private fun Project.settingsRepositoriesMode(): RepositoriesMode =
    (gradle as GradleInternal).settings.dependencyResolutionManagement.repositoriesMode.get()

  private fun Directory.forBnfFiles(action: (bnfFile: File) -> Unit) {
    asFileTree.filter { it.extension == "bnf" }.forEach(action)
  }

  private companion object {
    val INTELLIJ_REPOSITORIES =
      listOf(
        "https://cache-redirector.jetbrains.com/intellij-dependencies",
        "https://cache-redirector.jetbrains.com/intellij-repository/releases",
      )
  }
}

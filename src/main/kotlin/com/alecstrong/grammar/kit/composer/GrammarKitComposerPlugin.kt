package com.alecstrong.grammar.kit.composer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.grammarkit.GrammarKitPlugin
import org.jetbrains.grammarkit.path
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import java.io.File

open class GrammarKitComposerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    require(project.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
      "You need to apply the Kotlin/JVM plugin before Grammar-Kit-Composer."
    }

    project.pluginManager.apply(GrammarKitPlugin::class.java)

    // https://youtrack.jetbrains.com/issue/IDEA-301677
    val grammar = project.configurations.register("grammar") {
      it.isCanBeResolved = true
      it.isCanBeConsumed = false
      it.defaultDependencies {
        it.add(project.dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4"))
      }
    }

    val rootDir = project.layout.projectDirectory.dir("src${File.separatorChar}main${File.separatorChar}kotlin")
    rootDir.forBnfFiles { bnfFile ->
      val name = bnfFile.toRelativeString(rootDir.asFile).replace(File.separatorChar, '_').dropLast(4)
      val outputDirectory = project.layout.buildDirectory.dir("grammars${File.separatorChar}$name")

      val compose = project.tasks.register("createComposable${name}Grammar", BnfExtenderTask::class.java) {
        it.bnfFile.set(bnfFile)
        it.root.set(rootDir.asFile.absolutePath)
        it.outputDirectory.set(outputDirectory)
        it.group = "grammar"
        it.description = "Generate composable grammars from .bnf files."
      }

      val gen = project.tasks.register("generate${name}Parser", GenerateParserTask::class.java) { generateParserTask ->
        val outputs = getOutputs(
          bnf = bnfFile,
          outputDirectory = outputDirectory,
          root = rootDir.asFile.absolutePath,
        )

        generateParserTask.dependsOn(compose)
        generateParserTask.source.set(outputs.outputFile.path)
        generateParserTask.targetRoot.set(outputDirectory.path)
        generateParserTask.pathToParser.set(outputs.parserClassString)
        generateParserTask.pathToPsiRoot.set(outputs.psiPackage)
        generateParserTask.purgeOldFiles.set(true)
        generateParserTask.group = "grammar"

        generateParserTask.classpath(grammar)
        // https://github.com/JetBrains/gradle-grammar-kit-plugin/issues/108
        generateParserTask.sourceFile.convention(
          generateParserTask.source.map {
            project.layout.projectDirectory.file(it)
          },
        )
        generateParserTask.targetRootOutputDir.convention(
          generateParserTask.targetRoot.map {
            project.layout.projectDirectory.dir(it)
          },
        )
        generateParserTask.parserFile.convention(
          generateParserTask.pathToParser.map {
            project.layout.projectDirectory.file("${generateParserTask.targetRoot.get()}/$it")
          },
        )
        generateParserTask.psiDir.convention(
          generateParserTask.pathToPsiRoot.map {
            project.layout.projectDirectory.dir("${generateParserTask.targetRoot.get()}/$it")
          },
        )
      }

      (project.extensions.getByName("sourceSets") as SourceSetContainer)
        .getByName("main").java.srcDir(outputDirectory.map { it.asFile.relativeTo(project.projectDir) })

      project.tasks.named("compileKotlin").configure {
        it.dependsOn(gen)
      }

      if (project.plugins.hasPlugin("com.google.devtools.ksp")) {
        project.afterEvaluate {
          project.tasks.named("kspKotlin").configure {
            it.dependsOn(gen)
          }
        }
      }

      project.tasks.configureEach {
        if (it.name.contains("dokka") || it.name == "sourcesJar" || it.name == "javaSourcesJar") it.dependsOn(gen)
      }
    }
  }

  private fun Directory.forBnfFiles(action: (bnfFile: File) -> Unit) {
    asFileTree.filter {
      it.extension == "bnf"
    }.forEach(action)
  }
}

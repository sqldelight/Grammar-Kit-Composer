package com.alecstrong.grammar.kit.composer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.grammarkit.GrammarKitPlugin
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import java.io.File

open class GrammarKitComposerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(GrammarKitPlugin::class.java)
    project.file("src${File.separatorChar}main${File.separatorChar}kotlin").forBnfFiles { bnfFile ->
      val rootDir = project.file("src${File.separatorChar}main${File.separatorChar}kotlin")
      val name = bnfFile.toRelativeString(rootDir).replace(File.separatorChar, '_').dropLast(4)
      val outputDirectory = File(project.buildDir, "grammars${File.separatorChar}$name")

      val compose = project.tasks.register("createComposable${name}Grammar", BnfExtenderTask::class.java) {
        it.source(bnfFile)
        it.outputDirectory = outputDirectory
        it.include("**${File.separatorChar}*.bnf")
        it.group = "grammar"
        it.description = "Generate composable grammars from .bnf files."
      }

      val gen = project.tasks.create("generate${name}Parser", GenerateParserTask::class.java) { generateParserTask ->
        val outputs = getOutputs(
          bnf = bnfFile,
          outputDirectory = outputDirectory,
          root = rootDir,
        )

        generateParserTask.dependsOn(compose)
        generateParserTask.source.set(outputs.outputFile.path)
        generateParserTask.targetRoot.set(outputDirectory.path)
        generateParserTask.pathToParser.set(outputs.parserClass.toString().replace('.', File.separatorChar))
        generateParserTask.pathToPsiRoot.set(outputs.psiPackage.replace('.', File.separatorChar))
        generateParserTask.purgeOldFiles.set(true)
        generateParserTask.group = "grammar"
      }

      (project.extensions.getByName("sourceSets") as SourceSetContainer)
        .getByName("main").java.srcDir(outputDirectory.relativeTo(project.projectDir))

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

  private fun File.forBnfFiles(action: (bnfFile: File) -> Unit) {
    listFiles()?.forEach {
      if (it.isDirectory) it.forBnfFiles(action)
      if (it.extension == "bnf") action(it)
    }
  }
}

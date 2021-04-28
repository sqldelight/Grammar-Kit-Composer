package com.alecstrong.grammar.kit.composer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.grammarkit.GrammarKit
import org.jetbrains.grammarkit.tasks.GenerateParser
import java.io.File

open class GrammarKitComposerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(GrammarKit::class.java)
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

      val gen = project.tasks.create("generate${name}Parser", GenerateParser::class.java) { generateParserTask ->
        val outputs = getOutputs(
          bnf = bnfFile,
          outputDirectory = outputDirectory,
          root = rootDir
        )

        generateParserTask.dependsOn(compose)
        generateParserTask.source = outputs.outputFile
        generateParserTask.targetRoot = outputDirectory
        generateParserTask.pathToParser = outputs.parserClass.toString().replace('.', File.separatorChar)
        generateParserTask.pathToPsiRoot = outputs.psiPackage.replace('.', File.separatorChar)
        generateParserTask.purgeOldFiles = true
        generateParserTask.group = "grammar"
      }

      (project.extensions.getByName("sourceSets") as SourceSetContainer)
        .getByName("main").java.srcDir(outputDirectory.relativeTo(project.projectDir))

      project.tasks.named("compileKotlin").configure {
        it.dependsOn(gen)
      }

      project.tasks.configureEach {
        if (it.name.contains("dokka") || it.name == "sourcesJar") it.dependsOn(gen)
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

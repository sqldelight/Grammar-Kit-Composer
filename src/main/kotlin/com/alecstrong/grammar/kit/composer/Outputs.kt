package com.alecstrong.grammar.kit.composer

import com.squareup.kotlinpoet.ClassName
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkParameters
import java.io.File

internal interface Outputs : WorkParameters {
  val inputFile: RegularFileProperty

  val outputFile: RegularFileProperty
  val bnfFileName: Property<String>
  val psiPackage: Property<String>
  val outputPackage: Property<String>
  val outputDirectory: DirectoryProperty
}

internal val Outputs.parserClass get() = getParserClass(outputPackage.get(), bnfFileName.get())

private fun getParserClass(
  outputPackage: String,
  bnfFileName: String,
) = ClassName(outputPackage, "${bnfFileName.replaceFirstChar { it.titlecase() }}Parser")

internal fun getOutputs(
  outputDirectory: Provider<Directory>,
  bnf: File,
  root: String,
): ParserOutputs {
  val outputPackage = bnf.outputPackage(root)
  return ParserOutputs(
    outputFile = outputDirectory.map { it.file(bnf.generatedBnfFile) },
    parserClassString = getParserClass(outputPackage, bnf.nameWithoutExtension).toString().replace('.', File.separatorChar),
    psiPackage = outputPackage.psi.replace('.', File.separatorChar),
  )
}

internal val String.psi get() = "$this.psi"

internal data class ParserOutputs(
  val outputFile: Provider<RegularFile>,
  val parserClassString: String,
  val psiPackage: String,
)

internal val File.generatedBnfFile get() = "${nameWithoutExtension}_gen.bnf"

internal fun File.outputPackage(root: String): String = parentFile.toRelativeString(File(root)).replace(File.separatorChar, '.')

package com.alecstrong.grammar.kit.composer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import java.io.File

open class BnfExtenderTask : SourceTask() {
  @get:OutputDirectory lateinit var outputDirectory: File

  @TaskAction
  fun execute(inputs: InputChanges) {
    source.files.forEach {
      GrammarFile(
        file = it,
        outputs = getOutputs(outputDirectory, it, project.file("src${File.separatorChar}main${File.separatorChar}kotlin")),
      ).generateComposableGrammarFile()
    }
  }
}

private class GrammarFile(
  private val file: File,
  private val outputs: Outputs,
) {
  private var overrides: ClassName? = null

  fun generateComposableGrammarFile() {
    val rules = LinkedHashMap<String, String>()
    var currentRule = ""
    var currentRuleDefinition = ""
    var firstRule = ""
    var header = ""
    var generatedUtilSuperclass = ClassName("com.intellij.lang.parser", "GeneratedParserUtilBase")

    file.forEachLine { line ->
      val ruleSeparatorIndex = line.indexOf("::=")
      if (ruleSeparatorIndex >= 0) {
        val ruleName = line.substring(0 until ruleSeparatorIndex).trim()
        if (currentRule.isNotEmpty()) {
          // End the old rule if there was one.
          rules[currentRule] = currentRuleDefinition
        } else {
          firstRule = ruleName
          header = "{" + currentRuleDefinition.substringAfter('{')
        }
        currentRule = ruleName
        currentRuleDefinition = line.substring((ruleSeparatorIndex + 3) until line.length)
      } else {
        currentRuleDefinition += "\n$line"
      }
    }

    val regex = Regex("[\\s\\S]+parserUtilClass=\"([a-zA-Z.]*)\"[\\s\\S]+")
    regex.matchEntire(header)?.groupValues?.getOrNull(1)?.let {
      generatedUtilSuperclass = ClassName.bestGuess(it)
    }

    val parentParser = Regex("[\\s\\S]+overrides=\"([a-zA-Z.0-9_]*)\"[\\s\\S]+")
    parentParser.matchEntire(header)?.groupValues?.getOrNull(1)?.run {
      overrides = ClassName.bestGuess(this)
    }

    rules[currentRule] = currentRuleDefinition

    val unextendableRules = unextendableRules(header, rules.keys)
    val rulesToExtend = rules.filterNot { it.key in unextendableRules }
    val privateRules = rules.keys.filter { it.startsWith("private") }.map { it.substringAfter("private ") }
    val imports = mutableSetOf<String>()

    if (overrides != null) {
      imports.add("\"static $overrides.*\"")
    }

    val newRules = generateRules(firstRule, rulesToExtend, imports, privateRules)

    val keyFinder = Regex("([^a-zA-Z_]|^)(${unextendableSubclasses(header, rules.keys).joinToString("|")})([^a-zA-Z_]|$)")
    val unextendableRuleDefinitions = rules.filterKeys { it in unextendableRules }
      .map {
        if (it.key.startsWith("private")) {
          generateRule(it.key, it.value, rulesToExtend, privateRules, imports)
        } else {
          "${it.key} ::= ${it.value.subclassReplacements(keyFinder)}"
        }
      }
      .joinToString("\n")

    header = header.lines().filterNot { it.contains("parserUtilClass=") }.drop(1).joinToString("\n")
    header = if (header.contains("parserImports=[")) {
      header.replace("parserImports=[", imports.joinToString(separator = "\n", prefix = "parserImports = [\n").prependIndent("  "))
    } else {
      imports.joinToString(separator = "\n", prefix = "parserImports = [\n", postfix = "\n]\n").prependIndent("  ") + header
    }

    header = "{\n  parserUtilClass=\"${outputs.outputPackage}.${file.parserUtilName()}\"\n" +
      "  parserClass=\"${outputs.parserClass}\"\n" +
      "  elementTypeHolderClass=\"${outputs.psiPackage}.${file.elementTypeHolderName()}\"\n" +
      "  psiPackage=\"${outputs.psiPackage}\"\n" +
      "  psiImplPackage=\"${outputs.psiPackage}.impl\"\n" +
      header

    outputs.outputFile.createIfAbsent()
      .writeText("$header\n$newRules\n$unextendableRuleDefinitions")

    File(outputs.outputDirectory.path, "${file.parserUtilName()}.kt")
      .createIfAbsent()
      .writeText(
        generateParserUtil(
          rules = rulesToExtend,
          inputFile = file,
          superclass = generatedUtilSuperclass,
        ),
      )
  }

  private fun File.createIfAbsent() = apply {
    if (!exists()) {
      parentFile.mkdirs()
      createNewFile()
    }
  }

  private fun generateRules(
    firstRule: String,
    rules: Map<String, String>,
    imports: MutableSet<String>,
    privateRules: Collection<String>,
  ): String {
    val keyFinder = Regex("([^a-zA-Z_]|^)(${(rules.keys + privateRules).joinToString("|")})([^a-zA-Z_0-9]|$)")

    val builder = StringBuilder("root ::= ${firstRule.extensionReplacements(keyFinder, privateRules)}\n")
    for ((rule, definition) in rules) {
      builder.append(generateRule(rule, definition, rules, privateRules, imports))
    }
    return builder.toString()
  }

  private fun generateRule(
    rule: String,
    definition: String,
    rules: Map<String, String>,
    privateRules: Collection<String>,
    imports: MutableSet<String>,
  ): String {
    val keyFinder = Regex("([^a-zA-Z_]|^)(${(rules.keys + privateRules).joinToString("|")})([^a-zA-Z_0-9]|$)")
    val pinFinder = Regex("pin[\\s\\S]+=[^\n\r]*")
    val recoverWhileFinder = Regex("[\\s\\S]+recoverWhile *= *([a-zA-Z_]*)[\\s\\S]+")
    val builder = StringBuilder()
    val definition = definition.replace(Regex("\\{([a-zA-Z_]*)}")) {
      val externalRule = it.groupValues[1]
      imports.add("\"static ${overrides}Util.${externalRule.toFunctionName()}\"")
      return@replace "<<${externalRule.toFunctionName()} <<${externalRule}_real>>>>"
    }

    builder.append("fake $rule ::= $definition\n")
      .append(
        "${rule}_real ::= ${definition.extensionReplacements(keyFinder, privateRules)} {\n" +
          "  elementType = ${rule.substringAfter("private ")}\n",
      )
    pinFinder.find(definition)?.groupValues?.getOrNull(0)?.let {
      builder.append("  $it\n")
    }
    recoverWhileFinder.matchEntire(definition)?.groupValues?.getOrNull(1)?.let {
      builder.append("  recoverWhile=${it.extensionReplacements(keyFinder, privateRules)}\n")
    }
    builder.append("}\n")

    return builder.toString()
  }

  private fun String.extensionReplacements(keysRegex: Regex, privateRules: Collection<String>): String {
    fun String.matcher() = replace(keysRegex) { match ->
      if (match.groupValues[2] in privateRules) {
        "${match.groupValues[1]}${match.groupValues[2]}_real${match.groupValues[3]}"
      } else {
        "${match.groupValues[1]}<<${match.groupValues[2].toFunctionName()} ${match.groupValues[2]}_real>>${match.groupValues[3]}"
      }
    }
    if (trim().endsWith("}")) {
      return substring(0, lastIndexOf("{") - 1).matcher().matcher()
    }
    // We have to do it twice because the matcher doesn't catch three adjacent rules.
    return matcher().matcher()
  }

  private fun String.subclassReplacements(keysRegex: Regex): String {
    fun String.matcher() = replace(keysRegex) { match ->
      "${match.groupValues[1]}${match.groupValues[2]}_real${match.groupValues[3]}"
    }
    // We have to do it twice because the matcher doesn't catch three adjacent rules.
    return matcher().matcher()
  }

  private fun String.toFunctionName(): String {
    return "${toCustomFunction()}Ext"
  }

  private fun String.toCustomFunction(): String {
    return replace(snakeCaseRegex) { matchResult -> matchResult.value.trim('_').replaceFirstChar { it.titlecase() } }
  }

  private fun unextendableRules(headerText: String, rules: Collection<String>): Sequence<String> {
    val keyFinder = Regex("extends\\(\"([^)\"]+)\"\\)=([a-zA-Z_]+)\n")
    return keyFinder.findAll(headerText).map { it.groupValues[2] }.asSequence() + rules.filter { it.startsWith("private ") }
  }

  private fun unextendableSubclasses(headerText: String, rules: Collection<String>): Sequence<String> {
    val keyFinder = Regex("extends\\(\"([^)\"]+)\"\\)=([a-zA-Z_]+)\n")
    return keyFinder.findAll(headerText).flatMap {
      val pattern = Regex(it.groupValues[1])
      return@flatMap (rules.filter { it.matches(pattern) }).asSequence()
    }.distinct()
  }

  private fun File.parserUtilName() = "${nameWithoutExtension.replaceFirstChar { it.titlecase() }}ParserUtil"

  private fun File.elementTypeHolderName() = "${nameWithoutExtension.replaceFirstChar { it.titlecase() }}Types"

  private fun generateParserUtil(
    rules: Map<String, String>,
    inputFile: File,
    superclass: ClassName,
  ): String {
    val parserType = ClassName("com.intellij.lang.parser", "GeneratedParserUtilBase")
      .nestedClass("Parser")
    val elementTypeHolder = ClassName(outputs.psiPackage, inputFile.elementTypeHolderName())
    val astNodeType = ClassName("com.intellij.lang", "ASTNode")
    val psiElementType = ClassName("com.intellij.psi", "PsiElement")

    val overrideMethod = FunSpec.builder("override${overrides?.simpleName}")
    val overrideFinder = Regex("[\\s\\S]+override *= *([a-zA-Z_]*)[\\s\\S]+")
    fun ClassName.util() = ClassName(packageName, "${simpleName}Util")

    val resetMethod = FunSpec.builder("reset")
      .addStatement("createElement = { %T.Factory.createElement(it) }", elementTypeHolder)

    return FileSpec.builder(outputs.outputPackage, inputFile.parserUtilName())
      .addType(
        TypeSpec.objectBuilder(inputFile.parserUtilName())
          .superclass(superclass)
          .addProperty(
            PropertySpec.builder(
              name = "createElement",
              type = LambdaTypeName.get(
                parameters = arrayOf(astNodeType),
                returnType = psiElementType,
              ),
            )
              .mutable(true)
              .initializer("{ %T.Factory.createElement(it) }", elementTypeHolder)
              .build(),
          )
          .apply {
            rules.forEach { (key, definition) ->
              addProperty(
                PropertySpec.builder(key, parserType.copy(nullable = true))
                  .mutable(true)
                  .initializer("null")
                  .build(),
              )

              resetMethod.addStatement("$key = null")

              addFunction(
                FunSpec.builder(key.toFunctionName())
                  .addAnnotation(JvmStatic::class)
                  .addParameter("builder", ClassName("com.intellij.lang", "PsiBuilder"))
                  .addParameter("level", Int::class)
                  .addParameter(key, parserType)
                  .returns(Boolean::class)
                  .addStatement("return (this.$key ?: $key).parse(builder, level)")
                  .build(),
              )

              overrides?.let { overrides ->
                overrideFinder.matchEntire(definition)
                  ?.groupValues?.getOrNull(1)
                  ?.let {
                    if (it == "true") {
                      overrideMethod.addStatement(
                        "%T.$key = Parser { psiBuilder, i -> " +
                          "$key?.parse(psiBuilder, i) ?: %T.${key}_real(psiBuilder, i)" +
                          " }",
                        overrides.util(),
                        outputs.parserClass,
                      )
                    }
                  }
              }
            }

            addFunction(resetMethod.build())

            overrides?.let { overrides ->
              overrideMethod.addCode(
                """
                    val currentCreateElement = %T.createElement
                    %T.createElement = {
                      try {
                        createElement(it)
                      } catch(e: %T) {
                        currentCreateElement(it)
                      }
                    }
                """.trimIndent(),
                overrides.util(),
                overrides.util(),
                AssertionError::class.asTypeName(),
              )

              addFunction(overrideMethod.build())
            }
          }
          .build(),
      )
      .build()
      .toString()
  }

  companion object {
    private val snakeCaseRegex = Regex("_\\w")
  }
}

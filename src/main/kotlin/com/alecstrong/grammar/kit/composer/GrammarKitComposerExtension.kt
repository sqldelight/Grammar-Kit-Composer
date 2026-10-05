package com.alecstrong.grammar.kit.composer

import org.gradle.api.provider.Property

/**
 * Configuration for the `grammarKitComposer` extension. Taken from old plugin as these need to be
 * configured
 * https://github.com/JetBrains/gradle-grammar-kit-plugin/blob/master/src/main/kotlin/org/jetbrains/grammarkit/GrammarKitPluginExtension.kt``
 */
abstract class GrammarKitComposerExtension {
  /**
   * The IntelliJ Platform build number (for example `233.14808.21`) whose Maven artifacts are put
   * on the Grammar-Kit classpath when generating parsers.
   */
  abstract val intellijRelease: Property<String>

  /**
   * The Grammar-Kit version used to generate parsers. Defaults to the version bundled in the
   * IntelliJ Platform Gradle Plugin.
   */
  abstract val grammarKitRelease: Property<String>
}

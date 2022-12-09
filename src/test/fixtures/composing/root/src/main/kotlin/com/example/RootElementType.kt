package com.example

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

internal class RootElementType(debugName: String) : IElementType(debugName, RootLanguage)

object RootLanguage : Language("Root")

internal class RootTokenType(debugName: String) : IElementType(debugName, RootLanguage) {
  override fun toString(): String = "RootTokenType." + super.toString()
}

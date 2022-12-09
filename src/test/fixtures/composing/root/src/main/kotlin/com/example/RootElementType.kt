package com.example

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

class RootElementType(debugName: String) : IElementType(debugName, RootLanguage)

object RootLanguage : Language("Root")

class RootTokenType(debugName: String) : IElementType(debugName, RootLanguage) {
  override fun toString(): String = "RootTokenType." + super.toString()
}

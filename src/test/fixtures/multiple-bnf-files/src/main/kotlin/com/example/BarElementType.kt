package com.example

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

internal class BarElementType(debugName: String) : IElementType(debugName, BarLanguage)

object BarLanguage : Language("Bar")

internal class BarTokenType(debugName: String) : IElementType(debugName, FooLanguage) {
  override fun toString(): String = "BarTokenType." + super.toString()
}

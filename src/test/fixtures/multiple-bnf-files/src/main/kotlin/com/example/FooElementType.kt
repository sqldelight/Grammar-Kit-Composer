package com.example

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

internal class FooElementType(debugName: String) : IElementType(debugName, FooLanguage)

object FooLanguage : Language("Foo")

internal class FooTokenType(debugName: String) : IElementType(debugName, FooLanguage) {
  override fun toString(): String = "FooTokenType." + super.toString()
}

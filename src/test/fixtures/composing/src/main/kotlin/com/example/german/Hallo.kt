package com.example.german

import com.example.german.psi.FooExtendStmt
import com.example.german.psi.FooHelloStmt
import com.example.german.psi.FooMoin
import com.example.psi.RootDialog
import com.example.psi.RootPerson
import com.example.psi.RootSentences

abstract class Hallo : RootDialog {
  fun hello() {
    val helloStmt = helloStmtList.single()
    val name: RootPerson = helloStmt.person
    println(name)

    if (helloStmt is FooHelloStmt) {
      val moin: FooMoin? = helloStmt.moin
      println(moin)
    }
  }

  fun extending() {
    val ext: RootSentences = sentencesList.single()
    if (ext is FooExtendStmt) {
      val name: RootPerson = ext.goodbyeInternal.person
      println(name)
    }
  }
}

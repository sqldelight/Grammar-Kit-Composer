package com.example

import com.example.psi.RootHelloStmt
import com.example.psi.RootPerson

abstract class Root : RootHelloStmt {
  fun hello() {
    val goodbye: RootPerson = person
    println(goodbye)
  }
}

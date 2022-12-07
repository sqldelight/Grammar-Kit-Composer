package com.example

import com.example.psi.RootGreetingStmt
import com.example.psi.RootHelloStmt
import com.example.psi.RootNameStmt

abstract class Root : RootGreetingStmt {
  fun test() {
    val helloStmt: RootHelloStmt = helloStmt
    val nameStmt: RootNameStmt = nameStmt
  }
}

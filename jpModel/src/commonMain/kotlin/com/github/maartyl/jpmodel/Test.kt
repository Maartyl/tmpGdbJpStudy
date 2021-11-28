package com.github.maartyl.jpmodel

import com.github.maartyl.gdb.GRef
import kotlinx.serialization.Serializable

@Serializable
sealed class TestSeri

@Serializable
data class Test1(val a: String) : TestSeri()

@Serializable
data class Test2(val b: String, val i: Int) : TestSeri()

@Serializable
data class Test3(val b: String, val ti: GRef<Test1>) : TestSeri()
package org.jetbrains.fortran.debugger.lang

import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupport
import com.jetbrains.cidr.execution.debugger.CidrEvaluator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver.StandardDebuggerLanguage.FORTRAN
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelper
import com.jetbrains.cidr.execution.debugger.evaluation.CidrDebuggerTypesHelperBase
import com.jetbrains.cidr.execution.debugger.evaluation.CidrEvaluatedValue

class FortranDebuggerLanguageSupport : CidrDebuggerLanguageSupport() {
    override fun getSupportedDebuggerLanguages() = setOf(FORTRAN)

    override fun createDebuggerTypesHelper(process: CidrDebugProcess): CidrDebuggerTypesHelperBase {
        return FortranDebuggerTypesHelper(process)
    }

    override fun createEvaluator(frame: CidrStackFrame): CidrEvaluator = object : CidrEvaluator(frame) {
        override fun doEvaluate(driver: DebuggerDriver, position: XSourcePosition?, expr: XExpression): CidrEvaluatedValue {
            val v = driver.evaluate(frame.threadId, frame.frameIndex, expr.expression)
            return CidrEvaluatedValue(v, frame.process, position, frame, expr.expression)
        }
    }
}
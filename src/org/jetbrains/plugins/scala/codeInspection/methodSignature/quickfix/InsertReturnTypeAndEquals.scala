package org.jetbrains.plugins.scala
package codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, AbstractFix}
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class InsertReturnTypeAndEquals(functionDef: ScFunctionDefinition)
        extends AbstractFix(InspectionBundle.message("insert.return.type.and.equals"), functionDef) {

  def doApplyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    functionDef.removeAssignment()
    functionDef.removeExplicitType()
    val manager = functionDef.getManager
    val fakeDecl = ScalaPsiElementFactory.createDeclaration("x", "Unit", isVariable = false, null, manager)
    val colon = fakeDecl.findFirstChildByType(ScalaTokenTypes.tCOLON)
    val assign = fakeDecl.findFirstChildByType(ScalaTokenTypes.tASSIGN)
    val body = functionDef.body.get
    functionDef.addRangeAfter(colon, assign, body.getPrevSiblingNotWhitespace)
  }
}

package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.GroupNames
import com.intellij.codeInspection._
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import i18n.JavaCreatePropertyFix
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.ArrayList
import java.util.List
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaInvalidPropertyKeyInspection extends LocalInspectionTool {
  @NotNull override def getGroupDisplayName: String = {
    GroupNames.INTERNATIONALIZATION_GROUP_NAME
  }

  @NotNull override def getDisplayName: String = {
    CodeInsightBundle.message("inspection.unresolved.property.key.reference.name")
  }

  @NotNull override def getShortName: String = {
    "ScalaUnresolvedPropertyKey"
  }

  @NotNull override def getDefaultLevel: HighlightDisplayLevel = {
    HighlightDisplayLevel.ERROR
  }

  override def isEnabledByDefault: Boolean = {
    true
  }

  @Nullable override def checkFile(@NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val visitor: UnresolvedPropertyVisitor = new UnresolvedPropertyVisitor(manager, isOnTheFly)
    file.accept(visitor)
    val problems: List[ProblemDescriptor] = visitor.getProblems
    if (problems.isEmpty) null else problems.toArray(new Array[ProblemDescriptor](problems.size))
  }

  private object UnresolvedPropertyVisitor {
    def appendPropertyKeyNotFoundProblem(bundleName: String, @NotNull key: String,
                                         @NotNull expression: ScLiteral, @NotNull manager: InspectionManager,
                                         @NotNull problems: List[ProblemDescriptor], onTheFly: Boolean) {
      val description: String = CodeInsightBundle.message("inspection.unresolved.property.key.reference.message", key)
      val propertiesFiles: List[PropertiesFile] = filterNotInLibrary(expression.getProject,
        ScalaI18nUtil.propertiesFilesByBundleName(bundleName, expression))
      problems.add(manager.createProblemDescriptor(expression, description,
        if (propertiesFiles.isEmpty) null else new JavaCreatePropertyFix(expression, key, propertiesFiles),
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly))
    }

    @NotNull def filterNotInLibrary(@NotNull project: Project,
                                    @NotNull propertiesFiles: List[PropertiesFile]): List[PropertiesFile] = {
      val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
      val result: List[PropertiesFile] = new ArrayList[PropertiesFile](propertiesFiles.size)
      import scala.collection.JavaConversions._
      for (file <- propertiesFiles) {
        if (!fileIndex.isInLibraryClasses(file.getVirtualFile) && !fileIndex.isInLibrarySource(file.getVirtualFile)) {
          result.add(file)
        }
      }
      result
    }

    def isComputablePropertyExpression(myExpression: ScExpression): Boolean = {
      var expression = myExpression
      while (expression != null && expression.getParent.isInstanceOf[ScParenthesisedExpr]) {
        expression = expression.getParent.asInstanceOf[ScExpression]
      }
      expression != null && expression.getParent.isInstanceOf[ScExpression]
    }
  }

  class UnresolvedPropertyVisitor(myManager: InspectionManager, onTheFly: Boolean) extends ScalaRecursiveElementVisitor {
    override def visitLiteral(expression: ScLiteral) {
      val value: AnyRef = expression.getValue
      if (value == null || !value.isInstanceOf[String]) return
      val key: String = value.asInstanceOf[String]
      if (UnresolvedPropertyVisitor.isComputablePropertyExpression(expression)) return
      val resourceBundleName: Ref[String] = new Ref[String]
      if (!ScalaI18nUtil.isValidPropertyReference(myManager.getProject, expression, key, resourceBundleName)) {
        UnresolvedPropertyVisitor.appendPropertyKeyNotFoundProblem(resourceBundleName.get, key,
          expression, myManager, myProblems, onTheFly)
      }
      else
        expression.getParent match {
        case nvp: ScNameValuePair =>
          if (Comparing.equal(nvp.getName, AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
            val manager: PropertiesReferenceManager = PropertiesReferenceManager.getInstance(expression.getProject)
            val module: Module = ModuleUtilCore.findModuleForPsiElement(expression)
            if (module != null) {
              val propFiles: List[PropertiesFile] = manager.findPropertiesFiles(module, key)
              if (propFiles.isEmpty) {
                val description: String = CodeInsightBundle.message("inspection.invalid.resource.bundle.reference", key)
                val problem: ProblemDescriptor = myManager.createProblemDescriptor(expression, description, null.asInstanceOf[LocalQuickFix], ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly)
                myProblems.add(problem)
              }
            }
          }
        case expressions: ScArgumentExprList if expression.getParent.getParent.isInstanceOf[ScMethodCall] =>
          val annotationParams = new mutable.HashMap[String, AnyRef]
          annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
          if (!ScalaI18nUtil.mustBePropertyKey(myManager.getProject, expression, annotationParams)) return
          val paramsCount: java.lang.Integer = ScalaI18nUtil.getPropertyValueParamsMaxCount(expression)
          if (paramsCount == -1) return
          val methodCall: ScMethodCall = expressions.getParent.asInstanceOf[ScMethodCall]
          methodCall.getInvokedExpr match {
            case referenceExpression: ScReferenceExpression =>
              referenceExpression.resolve() match {
                case method: PsiMethod =>
                  val args: Array[ScExpression] = expressions.exprsArray
                  var i: Int = 0
                  var flag = true
                  while (i < args.length && flag) {
                    if (args(i) eq expression) {
                      val param: java.lang.Integer = args.length - i - 1
                      val parameters = method.getParameterList.getParameters
                      if (i + paramsCount >= args.length && method != null &&
                              method.getParameterList.getParametersCount == i + 2 &&
                              parameters(i + 1).isVarArgs) {
                        myProblems.add(myManager.createProblemDescriptor(methodCall,
                          CodeInsightBundle.message("property.has.more.parameters.than.passed", key, paramsCount, param),
                          onTheFly, new Array[LocalQuickFix](0), ProblemHighlightType.GENERIC_ERROR))
                      }
                      flag = false
                    }
                    i += 1
                    i
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
    }

    def getProblems: List[ProblemDescriptor] = {
      myProblems
    }

    private final val myProblems: List[ProblemDescriptor] = new ArrayList[ProblemDescriptor]
  }

}

package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.{PsiNamedElement, PsiElement, PsiReference}
import lang.resolve.ResolvableReferenceElement
import collection.JavaConverters.{asJavaCollectionConverter, iterableAsScalaIterableConverter}
import java.util
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.usageView.UsageInfo
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern

object ScalaRenameUtil {
  def filterAliasedReferences(allReferences: util.Collection[PsiReference]): util.ArrayList[PsiReference] = {
    val filtered = allReferences.asScala.filterNot(isAliased)
    new util.ArrayList(filtered.asJavaCollection)
  }

  def isAliased(ref: PsiReference): Boolean = ref match {
    case resolvableReferenceElement: ResolvableReferenceElement =>
      resolvableReferenceElement.bind() match {
        case Some(result) =>
          val renamed = result.isRenamed
          renamed.nonEmpty
        case None => false
      }
    case _ => false
  }

  def replaceImportClassReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    allReferences.asScala.map {
      case ref: ScStableCodeReferenceElement =>
        val isInImport = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null
        if (isInImport && ref.resolve() == null) {
          val multiResolve = ref.multiResolve(false)
          if (multiResolve.length > 1 && multiResolve.forall(_.getElement.isInstanceOf[ScTypeDefinition])) {
            new PsiReference {
              def getVariants: Array[AnyRef] = ref.getVariants

              def getCanonicalText: String = ref.getCanonicalText

              def getElement: PsiElement = ref.getElement

              def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              def bindToElement(element: PsiElement): PsiElement = ref.bindToElement(element)

              def handleElementRename(newElementName: String): PsiElement = ref.handleElementRename(newElementName)

              def isSoft: Boolean = ref.isSoft

              def getRangeInElement: TextRange = ref.getRangeInElement

              def resolve(): PsiElement = multiResolve.apply(0).getElement
            }
          } else ref
        } else ref
      case ref: PsiReference => ref
    }.asJavaCollection
  }

  def findSubstituteElement(elementToRename: PsiElement): PsiNamedElement = {
    elementToRename match {
      case primConstr: ScPrimaryConstructor => primConstr.containingClass
      case fun: ScFunction if fun.isConstructor => fun.containingClass
      case fun: ScFunction if Seq("apply", "unapply", "unapplySeq") contains fun.name =>
        fun.containingClass match {
          case newTempl: ScNewTemplateDefinition => ScalaPsiUtil.findInstanceBinding(newTempl).getOrElse(null)
          case clazz => clazz
        }
      case named: PsiNamedElement => named
      case _ => null
    }
  }

  def doRenameGenericNamedElement(namedElement: PsiElement,
                                  newName: String,
                                  usages: Array[UsageInfo],
                                  listener: RefactoringElementListener): Unit = {
    case class UsagesWithName(name: String, usages: Array[UsageInfo])

    val encodeNames: UsagesWithName => Seq[UsagesWithName] = {
      case UsagesWithName(name, usagez) =>
        if (usagez.isEmpty) Nil
        else {
          val encodedName = ScalaNamesUtil.toJavaName(newName)
          if (encodedName == name) Seq(UsagesWithName(name, usagez))
          else {
            val needEncodedName: UsageInfo => Boolean = { u =>
              val ref = u.getReference.getElement
              !ref.getLanguage.isInstanceOf[ScalaLanguage] //todo more concise condition?
            }
            val (usagesEncoded, usagesPlain) = usagez.partition(needEncodedName)
            Seq(UsagesWithName(encodedName, usagesEncoded), UsagesWithName(name, usagesPlain))
          }
        }
    }

    val modifyScObjectName: UsagesWithName => Seq[UsagesWithName] = {
      case UsagesWithName(name, usagez) => {
        if (usagez.isEmpty) Nil
        else {
          val needDollarSign: UsageInfo => Boolean = { u =>
            val ref = u.getReference.getElement
            !ref.getLanguage.isInstanceOf[ScalaLanguage]
          }
          val (usagesWithDS, usagesPlain) = usagez.partition(needDollarSign)
          Seq(UsagesWithName(name + "$", usagesWithDS), UsagesWithName(name, usagesPlain))
        }
      }
    }

    val modifySetterName: UsagesWithName => Seq[UsagesWithName] = {
      case arg @ UsagesWithName(name, usagez) => {
        if (usagez.isEmpty) Nil
        else {
          val newNameWithoutSuffix = name.stripSuffix(setterSuffix(name))
          val grouped = usagez.groupBy(u => setterSuffix(u.getElement.getText))
          grouped.map(entry => UsagesWithName(newNameWithoutSuffix + entry._1, entry._2)).toSeq
        }
      }
    }

    val encoded = encodeNames(UsagesWithName(newName, usages))
    val modified = namedElement match {
      case _: ScObject => encoded.flatMap(modifyScObjectName)
      case _: PsiTypedDefinitionWrapper | _: FakePsiMethod => encoded.flatMap(modifySetterName)
      case fun: ScFunction if setterSuffix(fun.name) != "" => encoded.flatMap(modifySetterName)
      case variable: ScReferencePattern => encoded.flatMap(modifySetterName)
      case _ => encoded
    }
    modified.foreach {
      case UsagesWithName(name, usagez) if usagez.nonEmpty =>
        RenameUtil.doRenameGenericNamedElement(namedElement, name, usagez, listener)
      case _ =>
    }
      //to guarantee correct name of namedElement itself
    RenameUtil.doRenameGenericNamedElement(namedElement, newName, Array.empty[UsageInfo], listener)
  }

  def setterSuffix(name: String) = {
    if (name.endsWith("_=")) "_="
    else if (name.endsWith("_$eq")) "_$eq"
    else ""
  }
}
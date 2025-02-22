/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.presentation.render
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFunctionOrLambda
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.types.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt

/**
 * Base class for converting the given `expr` to the type [ty] using trait [traitName]. The conversion process is
 * generated by the [fromCallMaker] function. Note the fix neither try to verify that the [traitName] actually exist,
 * nor check that the [traitName] is actually implemented for [ty]. The correctness of the code generated by the
 * [fromCallMaker] is also not checked.
 */
@Suppress("KDocUnresolvedReference")
abstract class ConvertToTyUsingTryTraitFix(
    expr: RsExpr,
    @SafeFieldForPreview
    private val ty: Ty,
    traitName: String,
    @SafeFieldForPreview
    private val fromCallMaker: ConvertToTyUsingTryTraitFix.(RsPsiFactory, RsExpr, Ty) -> RsExpr
) : ConvertToTyUsingTraitFix(expr, ty, traitName) {

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        val psiFactory = RsPsiFactory(project)
        val fromCall = fromCallMaker(psiFactory, startElement, ty)
        addFromCall(psiFactory, startElement, fromCall)
    }

    open fun addFromCall(psiFactory: RsPsiFactory, expr: RsExpr, fromCall: RsExpr) {
        expr.replace(fromCall)
    }
}

/**
 * Similar to [ConvertToTyUsingTryTraitFix], but also "unwraps" the result with `unwrap()` or `?`.
 */
abstract class ConvertToTyUsingTryTraitAndUnpackFix(
    expr: RsExpr,
    ty: Ty,
    @SafeFieldForPreview
    private val errTy: Ty,
    traitName: String,
    fromCallMaker: ConvertToTyUsingTryTraitFix.(RsPsiFactory, RsExpr, Ty) -> RsExpr
) : ConvertToTyUsingTryTraitFix(expr, ty, traitName, fromCallMaker) {

    override fun addFromCall(psiFactory: RsPsiFactory, expr: RsExpr, fromCall: RsExpr) {
        val parentFnRetTy = findParentFnOrLambdaRetTy(expr)
        when {
            parentFnRetTy != null && isFnRetTyResultAndMatchErrTy(expr, parentFnRetTy) ->
                expr.replace(psiFactory.createTryExpression(fromCall))
            else -> expr.replace(psiFactory.createNoArgsMethodCall(fromCall, "unwrap"))
        }
    }

    private fun findParentFnOrLambdaRetTy(element: RsExpr): Ty? =
        findParentFunctionOrLambdaRsRetType(element)?.typeReference?.normType

    private fun findParentFunctionOrLambdaRsRetType(element: RsExpr): RsRetType? {
        var parent = element.parent
        while (parent != null) {
            when (parent) {
                is RsFunctionOrLambda -> return parent.retType
                else -> parent = parent.parent
            }
        }
        return null
    }

    private fun isFnRetTyResultAndMatchErrTy(element: RsExpr, fnRetTy: Ty): Boolean {
        val (lookup, items) = element.implLookupAndKnownItems
        return fnRetTy is TyAdt && fnRetTy.item == items.Result
            && lookup.select(TraitRef(fnRetTy.typeArguments[1], (items.From
            ?: return false).withSubst(errTy))).ok() != null
    }
}

private const val TRY_FROM_TRAIT = "TryFrom"
private val TRY_FROM_CALL_MAKER: ConvertToTyUsingTryTraitFix.(RsPsiFactory, RsExpr, Ty) -> RsExpr =
    { psiFactory, expr, ty -> psiFactory.createAssocFunctionCall(ty.render(includeTypeArguments = false), "try_from", listOf(expr)) }

/**
 * For the given `expr` converts it to the type `Result<ty, _>` with `ty::try_from(expr)`.
 */
class ConvertToTyUsingTryFromTraitFix(expr: RsExpr, ty: Ty) :
    ConvertToTyUsingTryTraitFix(expr, ty, TRY_FROM_TRAIT, TRY_FROM_CALL_MAKER)

/**
 * For the given `expr` converts it to the type [ty] with `ty::try_from(expr).unwrap()` or `ty::try_from(expr)?` if
 * possible.
 */
class ConvertToTyUsingTryFromTraitAndUnpackFix(expr: RsExpr, ty: Ty, errTy: Ty) :
    ConvertToTyUsingTryTraitAndUnpackFix(expr, ty, errTy, TRY_FROM_TRAIT, TRY_FROM_CALL_MAKER)

private const val FROM_STR_TRAIT = "FromStr"
private val PARSE_CALL_MAKER: ConvertToTyUsingTryTraitFix.(RsPsiFactory, RsExpr, Ty) -> RsExpr =
    { psiFactory, expr, _ -> psiFactory.createNoArgsMethodCall(expr, "parse") }

/**
 * For the given `strExpr` converts it to the type `Result<ty, _>` with `strExpr.parse()`.
 */
class ConvertToTyUsingFromStrFix(strExpr: RsExpr, ty: Ty):
    ConvertToTyUsingTryTraitFix(strExpr, ty, FROM_STR_TRAIT, PARSE_CALL_MAKER)

/**
 * For the given `strExpr` converts it to the type [ty] with `strExpr.parse().unwrap()` or
 * `strExpr.parse()?` if possible.
 */
class ConvertToTyUsingFromStrAndUnpackFix(strExpr: RsExpr, ty: Ty, errTy: Ty) :
    ConvertToTyUsingTryTraitAndUnpackFix(strExpr, ty, errTy, FROM_STR_TRAIT, PARSE_CALL_MAKER)

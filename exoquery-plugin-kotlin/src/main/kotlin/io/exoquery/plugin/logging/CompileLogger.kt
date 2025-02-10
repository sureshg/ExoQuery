package io.exoquery.plugin.logging

import io.exoquery.plugin.Diagnostics.SQL
import io.exoquery.plugin.location
import io.exoquery.plugin.transform.LocateableContext
import io.exoquery.plugin.trees.LocationContext
import io.exoquery.plugin.trees.ParserContext
import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.KtRealPsiSourceElement
import org.jetbrains.kotlin.cli.common.fir.FirDiagnosticsCompilerResultsReporter
import org.jetbrains.kotlin.cli.common.fir.reportToMessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticReporterFactory
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticReporterWithContext
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.ContextIndependentParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.ABSTRACT_SUPER_CALL
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.CREATING_AN_INSTANCE_OF_ABSTRACT_CLASS
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.PLUGIN_AMBIGUOUS_INTERCEPTED_SYMBOL
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext
import org.jetbrains.kotlin.ir.PsiSourceManager
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.sourceElement

typealias Location = CompilerMessageSourceLocation

data class CompileLogger(val messageCollector: MessageCollector, val currentFileRaw: IrFile, val macroInvokeSite: IrElement, val contextReporter: KtDiagnosticReporterWithImplicitIrBasedContext, val reporter: BaseDiagnosticsCollector, val ctxReporter: KtDiagnosticReporterWithContext){
  val currentFile: IrFileEntry = currentFileRaw.fileEntry


  fun warn(msg: String) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, macroInvokeSite.location(currentFile))

  fun IrElement.toSourceElement(containingIrFile: IrFile): AbstractKtSourceElement? {
    return PsiSourceManager.findPsiElement(this, containingIrFile)?.let(::KtRealPsiSourceElement)
      ?: (this as? IrMetadataSourceOwner)?.metadata?.source
      ?: sourceElement()
    }

  fun report(msg: String, elem: IrExpression) {
    //println("-------------- $msg")

    messageCollector.report(CompilerMessageSeverity.WARNING, msg, macroInvokeSite.location(currentFile))

    //val srcElem = elem.toSourceElement(currentFileRaw) ?: throw IllegalArgumentException("Cannot get source of: ${elem.dumpKotlinLike()}")
    //val ctx = contextReporter.at(srcElem, elem, currentFileRaw)
    //reporter.reportOn(srcElem, SQL, msg, ctx, SourceElementPositioningStrategies.DEFAULT)

    //FirDiagnosticsCompilerResultsReporter.reportToMessageCollector(
    //  reporter,
    //  messageCollector,
    //  true
    //)

    // Same thing as above, but with the context reporter
    //reporter.reportToMessageCollector(messageCollector, true)
  }

  fun warn(msg: String, loc: Location) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, loc)

  // TODO need a "lesser thing" here than parser context, just need to know the current file so need to make a context with that
  context(ParserContext) fun warn(msg: String, elem: IrElement) =
    messageCollector.report(CompilerMessageSeverity.WARNING, msg, elem.location())

  fun error(msg: String) =
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, macroInvokeSite.location(currentFile))

  fun error(msg: String, loc: Location) {
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, loc)
  }

  context(LocateableContext) fun error(msg: String, elem: IrElement) {
    messageCollector.report(CompilerMessageSeverity.ERROR, msg, elem.location())
  }

  fun currentLocation() = macroInvokeSite.location(currentFile)

  companion object {
    operator fun invoke(config: CompilerConfiguration, currentFile: IrFile, macroInvokeSite: IrElement) =
      config.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE).let { msgCollector ->
        val reporter = DiagnosticReporterFactory.createReporter(msgCollector, true)
        val contextReporter = KtDiagnosticReporterWithImplicitIrBasedContext(reporter, config.languageVersionSettings)
        val ctxReporter = KtDiagnosticReporterWithContext(reporter, config.languageVersionSettings)
        CompileLogger(msgCollector, currentFile, macroInvokeSite, contextReporter, reporter, ctxReporter)
      }
  }
}

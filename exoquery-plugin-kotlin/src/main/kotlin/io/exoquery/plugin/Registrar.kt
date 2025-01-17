package io.exoquery.plugin

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.ContextIndependentParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import kotlin.String
import kotlin.io.path.Path

@AutoService(CompilerPluginRegistrar::class)
@OptIn(ExperimentalCompilerApi::class)
class Registrar: CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(GenerationExtension(
            configuration,
            configuration.getNotNull(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY),
            Path(configuration.getNotNull(PROJECT_DIR_KEY))
        ))
    }
}

object Diagnostics {
    inline fun <reified P : PsiElement, A> myWarning1(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT
    ): DiagnosticFactory1DelegateProvider<A> {
        return DiagnosticFactory1DelegateProvider(Severity.INFO, positioningStrategy, P::class)
    }

    val SQL: KtDiagnosticFactory1<String> by myWarning1<PsiElement, String>()
    val Renderer = object: ContextIndependentParameterRenderer<String> {
        override fun render(obj: String): String {
            return obj
        }
    }
    object KtMessages : BaseDiagnosticRendererFactory() {
        override val MAP =
            KtDiagnosticFactoryToRendererMap("Query Generated").also { map ->
                map.put(SQL, "{0}", Renderer)
            }
    }

    init {
        RootDiagnosticRendererFactory.registerFactory(KtMessages)
    }
}

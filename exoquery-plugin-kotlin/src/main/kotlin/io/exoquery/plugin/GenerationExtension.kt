package io.exoquery.plugin

import io.exoquery.plugin.settings.ExoCompileOptions
import io.exoquery.plugin.transform.FileQueryAccum
import io.exoquery.plugin.transform.TransformerScope
import io.exoquery.plugin.transform.VisitTransformExpressions
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import java.nio.file.Path

class GenerationExtension(
    private val config: CompilerConfiguration,
    private val messages: MessageCollector,
    private val exoOptions: ExoCompileOptions
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment
            .transform(
                VisitTransformExpressions(pluginContext, config, exoOptions),
                TransformerScope(listOf(), FileQueryAccum.Empty)
            )
    }
}

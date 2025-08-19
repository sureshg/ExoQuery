package io.exoquery.codegen.model

import io.exoquery.codegen.gen.PackagePath
import io.exoquery.codegen.util.indent

sealed interface CodeWrapperType {
  data object ObjectGen : CodeWrapperType
  data object PackageGen : CodeWrapperType

  fun makeWrapper(packagePath: PackagePath): CodeWrapper {
    return when (this) {
      ObjectGen -> CodeWrapper.ObjectGen(packagePath)
      PackageGen -> CodeWrapper.PackageGen(packagePath)
    }
  }
}

sealed interface CodeWrapper {
  val wrapperType: CodeWrapperType
  fun surround(innerCode: String): String

  data class ObjectGen(val packagePath: PackagePath): CodeWrapper {
    override val wrapperType = CodeWrapperType.ObjectGen
    private val packageDef get() = packagePath.prefix.toPackageStringOrNull()?.let { "package $it\n\n" } ?: ""

    companion object {
      val importBlock =
        """
        import io.exoquery.annotation.ExoValue
        import kotlinx.serialization.SerialName
        """.trimIndent()
    }

    override fun surround(innerCode: String): String = run {
      val objectName = packagePath.innermost
packageDef + importBlock +
"""
object $objectName {
  ${innerCode.indent(2)}
}
""".trimIndent()
  }
}

  data class PackageGen(val packagePath: PackagePath): CodeWrapper {
    override val wrapperType = CodeWrapperType.PackageGen
    private val packageDef get() = packagePath.fullPath().toPackageStringOrNull()?.let { "package $it\n\n" } ?: ""

    override fun surround(innerCode: String): String = run {
packageDef +
"""
import io.exoquery.annotation.ExoValue
import kotlinx.serialization.SerialName

$innerCode
""".trimMargin()
}

  }
}

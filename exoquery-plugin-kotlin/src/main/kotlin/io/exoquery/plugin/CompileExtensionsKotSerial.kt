package io.exoquery.plugin

import io.exoquery.plugin.trees.KnownSerializer
import org.jetbrains.kotlin.ir.backend.js.utils.regularArgs
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull


fun IrType.inferSerializer() = run {
  val propertyType = this

  // If there is a serializer defined on the type-annotation try to get it
  val serializerFromType =
    propertyType.getAnnotationArgs<kotlinx.serialization.Serializable>().firstOrNull()
      ?.let { it as? IrClassReference }?.let { KnownSerializer.Ref(it) }

  // If there is a serializer defined on the class, try to get it
  val serializerFromTypeOrClass =
    serializerFromType
      ?: propertyType.classOrNull?.owner?.getAnnotation<kotlinx.serialization.Serializable>()?.let { annotationCtor ->
        // Note that the despite the fact that `kotlinx.serialization.Serializable` has a default 1st argument (i.e. the `with`)
        // in the backend-IR when the argument is not explicitly specified on the type that is being annotated, there will be
        // zero args that show up in the `valueArguments` field. I believe this is by design so that the compiler-writer can
        // tell the serialization constructor (or any constructor for that matter) is being used with default values.
        val serializerArg = annotationCtor.regularArgs.firstOrNull()
        val serializerArgRef = serializerArg?.let { it as? IrClassReference }?.let { KnownSerializer.Ref(it) }
        serializerArgRef ?: KnownSerializer.Implicit
      }

  serializerFromTypeOrClass ?: KnownSerializer.None
}

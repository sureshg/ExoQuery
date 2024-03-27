package io.exoquery.annotation

@RequiresOptIn(message = "Only to be used in MyPackage")
@Retention(AnnotationRetention.BINARY)
annotation class ExoInternal

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class SqlVar

@Retention(AnnotationRetention.BINARY)
annotation class ExoMethodName(val name: String)

// TODO change to Passthorugh. Parsing XR should be default
@Retention(AnnotationRetention.BINARY)
annotation class ParseXR

@Retention(AnnotationRetention.BINARY)
annotation class MethodProducingXR(val callMethod: String)

// TODO having the lambda ident be a List<XR.Ident> where basically every single lambda-funciton has only
//      one is confusing. Maybe go back to having only one in this case. We can introduce new annotation/transformer
//      if we find a case where multiple are needed.
@Retention(AnnotationRetention.BINARY)
annotation class LambdaMethodProducingXR(val callMethod: String)

@Retention(AnnotationRetention.BINARY)
annotation class QueryClauseDirectMethod(val callMethod: String)

@Retention(AnnotationRetention.BINARY)
annotation class QueryClauseAliasedMethod(val callMethod: String)

@Retention(AnnotationRetention.BINARY)
annotation class QueryClauseUnitBind(val callMethod: String)


/*
Idea: Use annotations to tell the parsing system which expressions to parse and which functions to plug in for them

@QueryMethod("fromExpr")
@QueryLambdaMethod("fromExpr")

@MethodProducingXR("fromExpr")
@LambdaMethodProducingXR("fromExpr")

// Actually, maybe we don't even need to tie it to Query...

fun map(f: (T) -> R): Query<R> = error()
fun fromExpr(idents: List<XR.Ident>, etc....): Query<R> = QueryContainer(XR.Map(this.xr, f.ident, f.xr))

 */
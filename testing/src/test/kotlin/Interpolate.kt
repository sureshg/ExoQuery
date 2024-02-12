import io.exoquery.*
import io.exoquery.annotation.ExoInternal
import io.exoquery.fansi.Str
import io.exoquery.printing.format
import io.exoquery.xr.BetaReduction

data class In(val value: String)
data class Out(val parts: List<String>, val params: List<In>)

object StaticTerp: Interpolator<In, Out> {
  override fun interpolate(parts: () -> List<String>, params: () -> List<In>): Out =
    Out(parts(), params())
}

class InstanceTerp: Interpolator<In, Out> {
  override fun interpolate(parts: () -> List<String>, params: () -> List<In>): Out =
    Out(parts(), params())
}


fun main() {
//  printSource {
//    StaticTerp.interpolate({listOf("foo, bar")}, {listOf(In("baz"))})
//  }


  //StaticTerp("foo_${In("One")}_bar_${In("Two")}${In("Three")}")
  // TODO need to have a case for a single string const
  println(StaticTerp("foo ${In("123")}"))
}
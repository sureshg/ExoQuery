package io.exoquery.norm

import io.exoquery.xr.XR
import io.exoquery.xr.XRType

operator fun XR.Ident.Companion.invoke(id: String) = XR.Ident(id, XRType.Generic, XR.Location.Synth)
operator fun XR.Ident.Companion.invoke(name: String, type: XRType) = XR.Ident(name, type, XR.Location.Synth)
operator fun XR.Entity.Companion.invoke(id: String) = XR.Entity(id, XRType.Product("Test", emptyList()))

package io.exoquery.xr

operator fun XR.Ident.Companion.invoke(id: String) = XR.Ident(id, XRType.Generic)
operator fun XR.Entity.Companion.invoke(id: String) = XR.Entity(id, XRType.Generic)
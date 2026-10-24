package com.photorevive.android
object OutputNames {
 fun album(value:String):String = value.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").trim('.').take(80).ifBlank { "Restored" }
}

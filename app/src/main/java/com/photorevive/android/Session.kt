package com.photorevive.android
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
data class PhotoEntry(val uri:Uri,var state:String="pending",var output:String="",var error:String="")
class Session(context:Context) {
 private val prefs=context.getSharedPreferences("session",Context.MODE_PRIVATE)
 val entries=ArrayList<PhotoEntry>()
 var album:String=prefs.getString("album","Restored")?:"Restored"
 init {
  runCatching {
   val array=JSONArray(prefs.getString("entries","[]"))
   for(i in 0 until array.length()) { val item=array.getJSONObject(i);entries.add(PhotoEntry(Uri.parse(item.getString("uri")),item.optString("state","pending"),item.optString("output"),item.optString("error"))) }
  }.onFailure { entries.clear() }
 }
 @Synchronized fun save() {
  val array=JSONArray()
  entries.forEach { array.put(JSONObject().put("uri",it.uri.toString()).put("state",it.state).put("output",it.output).put("error",it.error)) }
  check(prefs.edit().putString("album",OutputNames.album(album)).putString("entries",array.toString()).commit()) { "Could not save progress. Check free storage." }
 }
}

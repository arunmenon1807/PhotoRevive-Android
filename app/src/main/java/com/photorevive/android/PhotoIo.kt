package com.photorevive.android
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import java.util.UUID

class PhotoIo(private val context:Context) {
 fun load(uri:Uri):Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
  val ratio=minOf(1.0,3200.0/maxOf(info.size.width,info.size.height))
  decoder.setTargetSize(maxOf(1,(info.size.width*ratio).toInt()),maxOf(1,(info.size.height*ratio).toInt()))
  decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
  decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
 }.let { decoded -> if(decoded.config==Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888,false).also { decoded.recycle() } }
 fun save(album:String,bitmap:Bitmap):Uri {
  val resolver=context.contentResolver
  val collection=MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
  val uri=resolver.insert(collection,ContentValues().apply {
   put(MediaStore.Images.Media.DISPLAY_NAME,"PhotoRevive_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
   put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
   put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PhotoRevive/${OutputNames.album(album)}/")
   put(MediaStore.Images.Media.IS_PENDING,1)
  }) ?: error("Could not create a photo. Check free storage.")
  try {
   resolver.openOutputStream(uri)?.use { stream -> check(bitmap.compress(Bitmap.CompressFormat.JPEG,97,stream)) { "JPEG encoding failed." } } ?: error("Storage could not be opened.")
   check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)>0)
   return uri
  } catch(e:Exception) { resolver.delete(uri,null,null);throw e }
 }
}

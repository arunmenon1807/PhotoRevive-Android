package com.photorevive.android
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.*

object Restoration {
 fun enhance(bitmap:Bitmap):Bitmap {
  val rgba=Mat();val bgr=Mat();val balanced=Mat();val lab=Mat();val denoised=Mat();val blur=Mat();val sharpened=Mat();val out=Mat()
  val channels=ArrayList<Mat>();val luminance=ArrayList<Mat>()
  val clahe=Imgproc.createCLAHE(1.5,Size(8.0,8.0))
  try {
   Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR)
   Core.split(bgr,channels)
   val means=channels.map { Core.mean(it).`val`[0] }
   val target=means.average()
   channels.forEachIndexed { i,c -> c.convertTo(c,-1,(target/max(means[i],1.0)).coerceIn(0.90,1.10)) }
   Core.merge(channels,balanced)
   Imgproc.cvtColor(balanced,lab,Imgproc.COLOR_BGR2Lab);Core.split(lab,luminance)
   clahe.apply(luminance[0],luminance[0]);Core.merge(luminance,lab);Imgproc.cvtColor(lab,balanced,Imgproc.COLOR_Lab2BGR)
   Imgproc.bilateralFilter(balanced,denoised,3,12.0,12.0)
   Imgproc.GaussianBlur(denoised,blur,Size(0.0,0.0),1.0)
   Core.addWeighted(denoised,1.15,blur,-0.15,0.0,sharpened)
   Imgproc.cvtColor(sharpened,out,Imgproc.COLOR_BGR2RGBA)
   return Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(out,it) }
  } finally { channels.forEach { it.release() };luminance.forEach { it.release() };clahe.clear();listOf(rgba,bgr,balanced,lab,denoised,blur,sharpened,out).forEach { it.release() } }
 }
 /** Candidate mask only: thin bright/dark structures can also be intentional detail. */
 fun detectScratches(bitmap:Bitmap):Bitmap {
  val rgba=Mat();val gray=Mat();val bright=Mat();val dark=Mat();val response=Mat();val hierarchy=Mat();val mask=Mat();val full=Mat()
  val contours=ArrayList<MatOfPoint>()
  val factor=min(1.0,1600.0/max(bitmap.width,bitmap.height))
  val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,Size(9.0,9.0))
  try {
   Utils.bitmapToMat(bitmap,rgba);Imgproc.resize(rgba,rgba,Size(bitmap.width*factor,bitmap.height*factor))
   Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
   Imgproc.morphologyEx(gray,bright,Imgproc.MORPH_TOPHAT,kernel);Imgproc.morphologyEx(gray,dark,Imgproc.MORPH_BLACKHAT,kernel)
   Core.max(bright,dark,response);Imgproc.threshold(response,response,32.0,255.0,Imgproc.THRESH_BINARY)
   Imgproc.findContours(response,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE)
   mask.create(gray.size(),CvType.CV_8UC1);mask.setTo(Scalar(0.0))
   contours.forEach { contour ->
    val points=MatOfPoint2f(*contour.toArray())
    try {
     val rect=Imgproc.minAreaRect(points);val short=min(rect.size.width,rect.size.height);val long=max(rect.size.width,rect.size.height)
     if(long>=14 && short<=6 && long/max(short,1.0)>=4 && Imgproc.contourArea(contour)<gray.total()*0.01)
      Imgproc.drawContours(mask,listOf(contour),-1,Scalar(255.0),Imgproc.FILLED)
    } finally { points.release() }
   }
   Imgproc.resize(mask,full,Size(bitmap.width.toDouble(),bitmap.height.toDouble()),0.0,0.0,Imgproc.INTER_NEAREST)
   Imgproc.cvtColor(full,full,Imgproc.COLOR_GRAY2RGBA)
   return Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(full,it) }
  } finally { contours.forEach { it.release() };kernel.release();listOf(rgba,gray,bright,dark,response,hierarchy,mask,full).forEach { it.release() } }
 }
 fun repair(bitmap:Bitmap,maskBitmap:Bitmap):Bitmap {
  require(bitmap.width==maskBitmap.width && bitmap.height==maskBitmap.height)
  val rgba=Mat();val bgr=Mat();val mask=Mat();val grayMask=Mat();val dst=Mat();val out=Mat()
  val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,Size(3.0,3.0))
  try {
   Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR)
   Utils.bitmapToMat(maskBitmap,mask);Imgproc.cvtColor(mask,grayMask,Imgproc.COLOR_RGBA2GRAY)
   Imgproc.threshold(grayMask,grayMask,127.0,255.0,Imgproc.THRESH_BINARY)
   val count=Core.countNonZero(grayMask)
   if(count==0)return bitmap.copy(Bitmap.Config.ARGB_8888,false)
   require(count<grayMask.total()*0.20) { "Mark a smaller area (under 20% of the photo). Repair narrow tears in sections." }
   Imgproc.dilate(grayMask,grayMask,kernel)
   Photo.inpaint(bgr,grayMask,dst,3.0,Photo.INPAINT_TELEA)
   Imgproc.cvtColor(dst,out,Imgproc.COLOR_BGR2RGBA)
   return Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(out,it) }
  } finally { kernel.release();listOf(rgba,bgr,mask,grayMask,dst,out).forEach { it.release() } }
 }
}

package com.photorevive.android
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class RepairView(context:Context,val photo:Bitmap):View(context) {
 private data class Stroke(val path:Path,val width:Float,val erase:Boolean)
 private val strokes=ArrayList<Stroke>()
 private val mask=Bitmap.createBitmap(photo.width,photo.height,Bitmap.Config.ARGB_8888)
 private var baseMask:Bitmap?=null
 private val maskCanvas=Canvas(mask)
 private val brush=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
 private val imagePaint=Paint(Paint.FILTER_BITMAP_FLAG)
 private val overlay=Paint().apply { colorFilter=ColorMatrixColorFilter(floatArrayOf(0f,0f,0f,0f,255f,0f,0f,0f,0f,65f,0f,0f,0f,0f,65f,0.6f,0f,0f,0f,0f)) }
 var brushWidth=16f
 var erase=false
 var moveMode=false
 private var zoom=1f;private var panX=0f;private var panY=0f
 private var current:Path?=null;private var lastX=0f;private var lastY=0f
 private var scale=1f;private var dx=0f;private var dy=0f
 private val detector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
  override fun onScale(d:ScaleGestureDetector):Boolean {
   val old=zoom;zoom=(zoom*d.scaleFactor).coerceIn(1f,6f);val ratio=zoom/old
   panX=(panX-(d.focusX-width/2f))*ratio+(d.focusX-width/2f)
   panY=(panY-(d.focusY-height/2f))*ratio+(d.focusY-height/2f)
   invalidate();return true
  }
 })
 init { mask.eraseColor(Color.BLACK);contentDescription="Photo repair canvas. Red marks show areas selected for repair. Pinch to zoom." }
 private fun geometry() { scale=min(width.toFloat()/photo.width,height.toFloat()/photo.height)*zoom;dx=(width-photo.width*scale)/2+panX;dy=(height-photo.height*scale)/2+panY }
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas);geometry()
  val rect=RectF(dx,dy,dx+photo.width*scale,dy+photo.height*scale)
  canvas.drawBitmap(photo,null,rect,imagePaint);canvas.drawBitmap(mask,null,rect,overlay)
 }
 private fun redrawMask() {
  mask.eraseColor(Color.BLACK);baseMask?.let { maskCanvas.drawBitmap(it,0f,0f,null) }
  strokes.forEach { s -> brush.strokeWidth=s.width;brush.color=if(s.erase)Color.BLACK else Color.WHITE;maskCanvas.drawPath(s.path,brush) }
  invalidate()
 }
 fun setCandidates(bitmap:Bitmap) { baseMask?.recycle();baseMask=bitmap.copy(Bitmap.Config.ARGB_8888,false);strokes.clear();redrawMask() }
 fun undo() { if(strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex);redrawMask() } else { baseMask?.recycle();baseMask=null;redrawMask() } }
 fun clear() { strokes.clear();baseMask?.recycle();baseMask=null;redrawMask() }
 fun snapshot():Bitmap=mask.copy(Bitmap.Config.ARGB_8888,false)
 override fun onTouchEvent(event:MotionEvent):Boolean {
  detector.onTouchEvent(event);geometry()
  if(event.pointerCount>1) { current=null;return true }
  val x=((event.x-dx)/scale).coerceIn(0f,photo.width-1f);val y=((event.y-dy)/scale).coerceIn(0f,photo.height-1f)
  when(event.actionMasked) {
   MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true);lastX=event.x;lastY=event.y
    if(!moveMode) { current=Path().apply { moveTo(x,y);lineTo(x+0.01f,y+0.01f) };strokes.add(Stroke(current!!,brushWidth,erase));redrawMask() }
   }
   MotionEvent.ACTION_MOVE -> if(moveMode) { panX+=event.x-lastX;panY+=event.y-lastY;lastX=event.x;lastY=event.y;invalidate() } else { current?.lineTo(x,y);redrawMask() }
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { current=null;performClick() }
  };return true
 }
 override fun performClick():Boolean { super.performClick();return true }
 fun dispose() { mask.recycle();baseMask?.recycle();baseMask=null }
}

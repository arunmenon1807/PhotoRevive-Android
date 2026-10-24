package com.photorevive.android
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.min
class CompareView(context:Context,private val before:Bitmap,private val after:Bitmap):View(context) {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private var split=0.5f
 init { contentDescription="Before and after comparison. Drag left or right to reveal original and restored photo." }
 override fun onDraw(canvas:Canvas) {
  val scale=min(width.toFloat()/before.width,height.toFloat()/before.height)
  val rect=RectF((width-before.width*scale)/2,(height-before.height*scale)/2,(width+before.width*scale)/2,(height+before.height*scale)/2)
  canvas.drawBitmap(before,null,rect,paint)
  canvas.save();canvas.clipRect(width*split,0f,width.toFloat(),height.toFloat());canvas.drawBitmap(after,null,rect,paint);canvas.restore()
  paint.color=Color.WHITE;paint.strokeWidth=3f;canvas.drawLine(width*split,0f,width*split,height.toFloat(),paint)
  paint.textSize=14*resources.displayMetrics.scaledDensity;canvas.drawText("Original",12f,30f*resources.displayMetrics.density,paint)
  val text="Restored";canvas.drawText(text,width-paint.measureText(text)-12f,30f*resources.displayMetrics.density,paint)
 }
 override fun onTouchEvent(e:MotionEvent):Boolean { split=(e.x/width).coerceIn(0.02f,0.98f);invalidate();if(e.action==MotionEvent.ACTION_UP)performClick();return true }
 override fun performClick():Boolean { super.performClick();return true }
}

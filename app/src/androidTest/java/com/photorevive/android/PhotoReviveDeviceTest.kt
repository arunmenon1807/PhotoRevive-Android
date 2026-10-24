package com.photorevive.android
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import android.widget.ListView
import androidx.test.espresso.Espresso.onData
import org.hamcrest.Matchers.anything
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PhotoReviveDeviceTest {
 private val context:Context get()=ApplicationProvider.getApplicationContext()
 @Before fun init() { assertTrue(OpenCVLoader.initLocal()) }
 private fun scratched():Bitmap=Bitmap.createBitmap(360,240,Bitmap.Config.ARGB_8888).also {
  val canvas=Canvas(it);canvas.drawColor(Color.rgb(80,110,140))
  canvas.drawLine(180f,30f,180f,210f,Paint().apply { color=Color.WHITE;strokeWidth=3f })
 }
 private fun mask():Bitmap=Bitmap.createBitmap(360,240,Bitmap.Config.ARGB_8888).also {
  val canvas=Canvas(it);canvas.drawColor(Color.BLACK)
  canvas.drawLine(180f,25f,180f,215f,Paint().apply { color=Color.WHITE;strokeWidth=7f })
 }
 private fun digest(uri:Uri):ByteArray=context.contentResolver.openInputStream(uri)!!.use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
 @Test fun repairsScratchAndNarrowTearWithoutChangingSource() {
  val photo=scratched();val selection=mask()
  val repaired=Restoration.repair(photo,selection)
  try {
   assertEquals(Color.WHITE,photo.getPixel(180,100))
   val pixel=repaired.getPixel(180,100)
   assertTrue("Fill should approach surrounding colour",kotlin.math.abs(Color.red(pixel)-80)<30)
   assertTrue(kotlin.math.abs(Color.blue(pixel)-140)<30)
   assertEquals(photo.width,repaired.width);assertEquals(photo.height,repaired.height)
  } finally { repaired.recycle();selection.recycle();photo.recycle() }
 }
 @Test fun detectsThinScratchCandidates() {
  val photo=scratched();val result=Restoration.detectScratches(photo)
  try { assertTrue("Known scratch should be marked",Color.red(result.getPixel(180,100))>200) }
  finally { photo.recycle();result.recycle() }
 }
 @Test fun refusesOverlyLargeRepairArea() {
  val photo=scratched();val all=mask().apply { eraseColor(Color.WHITE) }
  try { try { Restoration.repair(photo,all);fail("Large mask must be rejected") } catch(expected:IllegalArgumentException) { assertTrue(expected.message!!.contains("smaller")) } }
  finally { photo.recycle();all.recycle() }
 }
 @Test fun enhancesAndHandlesEmptyMaskWithoutModifyingSource() {
  val photo=scratched();val originalPixel=photo.getPixel(180,100)
  val enhanced=Restoration.enhance(photo)
  val blank=mask().apply { eraseColor(Color.BLACK) };val unchanged=Restoration.repair(photo,blank)
  try {
   assertEquals(photo.width,enhanced.width);assertEquals(photo.height,enhanced.height)
   assertEquals(originalPixel,photo.getPixel(180,100));assertTrue(photo.sameAs(unchanged))
  } finally { enhanced.recycle();blank.recycle();unchanged.recycle();photo.recycle() }
 }
 @Test fun automaticColourIsIdenticalToManualEnhancement() {
  val source=scratched()
  val manual=Restoration.enhance(source)
  val automatic=Restoration.process(source,true,false)
  try { assertTrue("Automatic and manual colour must use identical processing",manual.sameAs(automatic));assertEquals(Color.WHITE,source.getPixel(180,100)) }
  finally { source.recycle();manual.recycle();automatic.recycle() }
 }
 @Test fun savesJpegIntoMediaStore() {
  val photo=scratched();val io=PhotoIo(context);val uri=io.save("Device test",photo)
  try {
   val loaded=io.load(uri);try { assertEquals(360,loaded.width);assertEquals(240,loaded.height) } finally { loaded.recycle() }
   context.contentResolver.query(uri,arrayOf(android.provider.MediaStore.Images.Media.RELATIVE_PATH),null,null,null)!!.use {
    assertTrue(it.moveToFirst());assertEquals("Pictures/PhotoRevive/Device test/",it.getString(0))
   }
  } finally { photo.recycle();context.contentResolver.delete(uri,null,null) }
 }
 @Test fun processesTwoPhotoBatchAndPreservesOriginalFiles() {
  context.getSharedPreferences("session",Context.MODE_PRIVATE).edit().clear().commit()
  context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().clear().commit()
  val io=PhotoIo(context);val photo=scratched()
  val first=io.save("Test inputs",photo);val second=io.save("Test inputs",photo);photo.recycle()
  val before=digest(first);val otherBefore=digest(second)
  val session=Session(context);session.album="Batch test";session.entries.add(PhotoEntry(first));session.entries.add(PhotoEntry(second));session.save()
  val outputs=ArrayList<Uri>()
  try {
   ActivityScenario.launch(MainActivity::class.java).use {
    onView(withText("PhotoRevive")).check(matches(isDisplayed()))
    onView(withText("Enhance / repair all pending photos")).perform(click())
    onView(withText("Start")).perform(click())
    var complete=false
    for(attempt in 0 until 60) {
     Thread.sleep(500)
     val current=Session(context)
     if(current.entries.size==2 && current.entries.all { e -> e.state=="done" }) { complete=true;outputs.addAll(current.entries.map { e -> Uri.parse(e.output) });break }
     if(current.entries.any { e -> e.state=="failed" }) fail("Batch failed: ${current.entries.map { e -> e.error }}")
    }
    assertTrue("Batch should save both images",complete)
    assertArrayEquals(before,digest(first));assertArrayEquals(otherBefore,digest(second))
    assertEquals(2,outputs.distinct().size);assertFalse(outputs.contains(first));assertFalse(outputs.contains(second))
    val source=io.load(first);val expected=Restoration.enhance(source);val saved=io.load(outputs[0])
    try {
     var difference=0L
     for(y in 10 until source.height-10 step 5)for(x in 10 until source.width-10 step 5) {
      val a=expected.getPixel(x,y);val b=saved.getPixel(x,y)
      difference+=kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.green(a)-Color.green(b))+kotlin.math.abs(Color.blue(a)-Color.blue(b))
     }
     assertTrue("Saved batch JPEG must match manual enhancement within JPEG tolerance",difference<15000)
     assertFalse("Batch must not save the original unchanged",source.sameAs(saved))
    } finally { source.recycle();expected.recycle();saved.recycle() }
    // The saved row must open the exported result, rather than a fresh original.
    onData(anything()).inAdapterView(isAssignableFrom(ListView::class.java)).atPosition(0).perform(click())
    var opened=false
    for(attempt in 0 until 40) {
     Thread.sleep(250)
     try { onView(withText("Saved result / original")).check(matches(isDisplayed()));opened=true;break } catch(e:AssertionError) {} catch(e:androidx.test.espresso.NoMatchingViewException) {}
    }
    assertTrue("Saved photo should open its saved-result comparison",opened)
   }
  } finally {
   outputs.forEach { context.contentResolver.delete(it,null,null) }
   context.contentResolver.delete(first,null,null);context.contentResolver.delete(second,null,null)
   context.getSharedPreferences("session",Context.MODE_PRIVATE).edit().clear().commit()
  }
 }
}

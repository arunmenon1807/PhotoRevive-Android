package com.photorevive.android
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import org.opencv.android.OpenCVLoader
import java.util.concurrent.Executors

class MainActivity:ComponentActivity() {
 private lateinit var root:LinearLayout
 private lateinit var session:Session
 private lateinit var io:PhotoIo
 private val worker=Executors.newSingleThreadExecutor()
 private var busy=false
 @Volatile private var stopRequested=false
 @Volatile private var closed=false
 private var batching=false
 private var screen="home"
 private var enhance=true
 private var autoRepair=false
 private var selected=0
 private var original:Bitmap?=null
 private var working:Bitmap?=null
 private var repairView:RepairView?=null
 private var albumInput:EditText?=null
 private var ready=false
 private val pickPhotos=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  if(uris.isNotEmpty()) {
   uris.forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
   addPhotos(uris)
  }
 }
 private val pickFolder=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if(uri!=null) {
   runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   task {
    val images=DocumentFile.fromTreeUri(this,uri)?.listFiles()?.filter { it.isFile && (it.type?.startsWith("image/")==true || it.name?.substringAfterLast('.')?.lowercase() in listOf("jpg","jpeg","png","webp","heic")) }?.map { it.uri }?:emptyList()
    ui { addPhotos(images);if(images.isEmpty())toast("No photos found directly in that folder.") }
   }
  }
 }
 override fun onCreate(state:Bundle?) {
  super.onCreate(state)
  session=Session(this);io=PhotoIo(this)
  enhance=getPreferences(MODE_PRIVATE).getBoolean("enhance",true)
  autoRepair=getPreferences(MODE_PRIVATE).getBoolean("repair",false)
  ready=OpenCVLoader.initLocal()
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
   override fun handleOnBackPressed() {
    if(batching) { stopRequested=true;toast("Pausing after the current photo.");return }
    if(busy) { toast("Please wait for this photo to finish.");return }
    if(screen=="home") finish() else android.app.AlertDialog.Builder(this@MainActivity).setMessage("Leave this editor? Unsaved edits will be discarded; the original stays unchanged.").setPositiveButton("Leave") { _,_ -> releaseEditor();home() }.setNegativeButton("Keep editing",null).show()
   }
  })
  home()
 }
 private fun ui(action:()->Unit) { runOnUiThread { if(!closed)action() } }
 private fun toast(text:String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
 private fun base(title:String) {
  albumInput=null
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(20,31,31)) }
  ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets -> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(20,bars.top+12,20,bars.bottom+12);insets }
  setContentView(root);label(title,26f)
 }
 private fun label(value:String,size:Float=15f):TextView = TextView(this).apply { text=value;textSize=size;setTextColor(Color.WHITE);setPadding(4,6,4,6);root.addView(this) }
 private fun button(value:String,action:()->Unit):Button = Button(this).apply {
  text=value;isAllCaps=false;minHeight=0;minimumHeight=0
  root.addView(this,LinearLayout.LayoutParams(-1,-2))
  setOnClickListener { if(!busy)action() else toast("Please wait for the current operation.") }
 }
 private fun rowButtons(vararg actions:Pair<String,()->Unit>) {
  val row=LinearLayout(this)
  actions.forEach { (text,action) -> row.addView(Button(this).apply { this.text=text;isAllCaps=false;minHeight=0;minimumHeight=0;setOnClickListener { if(!busy)action() } },LinearLayout.LayoutParams(0,-2,1f)) }
  root.addView(row)
 }
 private fun task(action:()->Unit) {
  if(busy)return;busy=true
  worker.execute { try { action() } catch(e:Exception) { ui { toast(e.message?:"Could not process this photo. Try a smaller image.") } } finally { ui { busy=false } } }
 }
 private fun rememberAlbum() { albumInput?.let { session.album=OutputNames.album(it.text.toString()) };session.save() }
 private fun addPhotos(uris:List<Uri>) {
  val known=session.entries.map { it.uri }.toHashSet()
  uris.distinct().filter { it !in known }.forEach { session.entries.add(PhotoEntry(it)) }
  runCatching { session.save() }.onFailure { toast(it.message?:"Could not store selection.") };home()
 }
 private fun home() {
  screen="home";base("PhotoRevive")
  label("Bring old photos back to life. Entirely on your phone.",14f)
  rowButtons("Select photos" to { rememberAlbum();pickPhotos.launch(arrayOf("image/*")) },"Select folder" to { rememberAlbum();pickFolder.launch(null) })
  albumInput=EditText(this).apply { setSingleLine();hint="Output album";setText(session.album);setTextColor(Color.WHITE);root.addView(this) }
  root.addView(CheckBox(this).apply { text="Colour, exposure, denoise and gentle sharpening";setTextColor(Color.WHITE);isChecked=enhance;setOnCheckedChangeListener { _,value -> enhance=value;getPreferences(MODE_PRIVATE).edit().putBoolean("enhance",value).apply() } })
  root.addView(CheckBox(this).apply { text="Also repair detected thin scratches in batch";setTextColor(Color.WHITE);isChecked=autoRepair;setOnCheckedChangeListener { _,value -> autoRepair=value;getPreferences(MODE_PRIVATE).edit().putBoolean("repair",value).apply() } })
  label("Scratch detection may select real detail. Review a photo first. Paint narrow tears in the editor; large missing areas cannot be reliably rebuilt.",12f)
  val done=session.entries.count { it.state=="done" };val pending=session.entries.count { it.state=="pending" };val failed=session.entries.count { it.state=="failed" }
  label("${session.entries.size} selected • $done saved • $pending pending • $failed failed",13f)
  val list=ListView(this)
  list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,session.entries.mapIndexed { i,e -> "${i+1}. ${if(e.state=="done")"Saved ✓" else if(e.state=="failed")"Failed: ${e.error.take(65)}" else "Tap for auto preview / repair"}" })
  root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
  list.setOnItemClickListener { _,_,position,_ -> if(!busy) { rememberAlbum();openPhoto(position) } }
  button("Enhance / repair all pending photos") {
   rememberAlbum()
   if(!ready) { toast("OpenCV could not load. Restart PhotoRevive.");return@button }
   if(pending==0) { toast("Select photos or retry failures first.");return@button }
   if(!enhance && !autoRepair) { toast("Choose enhancement or scratch repair first.");return@button }
   android.app.AlertDialog.Builder(this).setTitle("Process $pending photos?").setMessage("Save new JPEG copies in Pictures/PhotoRevive/${session.album}/. Originals are never changed. Keep the app open; leaving pauses after the current photo.${if(autoRepair)"\n\nAutomatic scratch repair can remove real lines or fine details. Review results carefully." else ""}").setPositiveButton("Start") { _,_ -> startBatch() }.setNegativeButton("Cancel",null).show()
  }
  rowButtons("Retry failures" to {
   session.entries.filter { it.state=="failed" }.forEach { it.state="pending";it.error="" };session.save();home()
  },"Clear selection" to {
   android.app.AlertDialog.Builder(this).setMessage("Clear this queue? Saved photos and originals stay on your phone.").setPositiveButton("Clear") { _,_ -> session.entries.clear();session.save();home() }.setNegativeButton("Cancel",null).show()
  })
  label("New JPEGs: Pictures/PhotoRevive/${session.album}/\nTap a saved row to compare its saved result with the original.",12f)
 }
 private fun openPhoto(position:Int) {
  if(!ready) { toast("Image processing is unavailable. Restart the app.");return }
  task {
   val entry=session.entries[position]
   val source=io.load(entry.uri)
   val saved=entry.state=="done" && entry.output.isNotBlank()
   val copy=try {
    if(saved) io.load(Uri.parse(entry.output))
    else Restoration.process(source,enhance,false)
   } catch(e:Exception) { source.recycle();throw e }
   ui { releaseEditor();original=source;working=copy;selected=position;if(saved || enhance)preview() else editor() }
   if(closed) { source.recycle();copy.recycle() }
  }
 }
 private fun editor() {
  repairView?.dispose();repairView=null
  screen="editor";base("Repair • ${selected+1} / ${session.entries.size}")
  label("Paint over scratches or narrow tears. Pinch to zoom; use Move to pan. Red marks show the repair area.",13f)
  val view=RepairView(this,working?:return);repairView=view
  root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
  val brushLabel=label("Brush: 16 photo pixels",12f)
  root.addView(SeekBar(this).apply { max=77;progress=13;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) { view.brushWidth=(p+3).toFloat();brushLabel.text="Brush: ${p+3} photo pixels" }
   override fun onStartTrackingTouch(s:SeekBar?) {}
   override fun onStopTrackingTouch(s:SeekBar?) {}
  }) })
  rowButtons("Paint" to { view.moveMode=false;view.erase=false },"Erase" to { view.moveMode=false;view.erase=true },"Move" to { view.moveMode=true },"Undo" to { view.undo() })
  rowButtons("Clear marks" to { view.clear() },"Detect scratches" to {
   android.app.AlertDialog.Builder(this).setMessage("Replace marks with detected thin-scratch candidates? They may include real detail. Erase unwanted marks before repair.").setPositiveButton("Detect") { _,_ ->
    task { val mask=Restoration.detectScratches(working!!);ui { view.setCandidates(mask);mask.recycle();toast("Review the red marks before repairing.") } }
   }.setNegativeButton("Cancel",null).show()
  })
  rowButtons("Enhance colour" to {
   task { val result=Restoration.enhance(working!!);ui { val old=working;working=result;preview();old?.recycle() } }
  },"Repair marked" to {
   val mask=view.snapshot()
   task { try { val result=Restoration.repair(working!!,mask);ui { val old=working;working=result;preview();old?.recycle() } } finally { mask.recycle() } }
  })
  button("Compare / save") { preview() }
 }
 private fun preview() {
  repairView?.dispose();repairView=null
  screen="preview";base(if(session.entries[selected].state=="done")"Saved result / original" else "Enhanced preview / original")
  label("Drag the divider to compare. Original on the left; restored on the right.",13f)
  root.addView(CompareView(this,original!!,working!!),LinearLayout.LayoutParams(-1,0,1f))
  rowButtons("Continue repair" to { editor() },"Reset edits" to { val old=working;working=original!!.copy(Bitmap.Config.ARGB_8888,false);editor();old?.recycle() })
  button("Save restored copy") {
   task {
    val uri=io.save(session.album,working!!)
    val entry=session.entries[selected];entry.state="done";entry.output=uri.toString();entry.error="";session.save()
    ui { toast("Saved a new JPEG to Pictures/PhotoRevive/${session.album}/");releaseEditor();home() }
   }
  }
  button("Back to selection") { onBackPressedDispatcher.onBackPressed() }
 }
 private fun startBatch() {
  if(busy)return
  busy=true;batching=true;stopRequested=false;screen="batch";base("Restoring your photos")
  val status=label("Preparing…",18f)
  val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=session.entries.count { it.state=="pending" } };root.addView(progress)
  label("Leave this screen open. Photos are processed one at a time. You can pause and resume; originals are never overwritten.")
  Button(this).apply { text="Pause after current photo";root.addView(this);setOnClickListener { stopRequested=true;text="Pausing…" } }
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  val album=session.album;val doEnhance=enhance;val doRepair=autoRepair
  worker.execute {
   var completed=0
   try {
    for((i,entry) in session.entries.withIndex()) {
     if(stopRequested || closed)break
     if(entry.state!="pending")continue
     ui { status.text="Processing photo ${i+1} of ${session.entries.size}…" }
     var current:Bitmap?=null
     try {
      current=io.load(entry.uri)
      val processed=Restoration.process(current,doEnhance,doRepair)
      current.recycle();current=processed
      val output=io.save(album,current)
      entry.state="done";entry.output=output.toString();entry.error=""
     } catch(e:Exception) { entry.state="failed";entry.error=e.message?:"Unreadable image or storage error" }
     finally { current?.recycle() }
     session.save();completed++
     ui { progress.progress=completed }
    }
   } catch(e:Exception) { ui { toast(e.message?:"Batch paused. Check free storage.") } }
   finally { ui {
    busy=false;batching=false;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    home();toast(if(stopRequested)"Paused. Tap process pending to resume." else "Finished. Saved copies are in Pictures/PhotoRevive/$album/.")
   } }
  }
 }
 private fun releaseEditor() { repairView?.dispose();repairView=null;working?.recycle();original?.recycle();working=null;original=null }
 override fun onStop() { if(batching)stopRequested=true;super.onStop() }
 override fun onDestroy() { closed=true;stopRequested=true;worker.shutdown();if(!busy)releaseEditor();super.onDestroy() }
}

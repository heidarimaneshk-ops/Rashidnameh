package com.rashidnameh.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
 private var descriptor: ParcelFileDescriptor? = null
 private var renderer: PdfRenderer? = null
 private var pageIndex = 0
 private lateinit var pageView: ZoomImageView
 private lateinit var label: TextView
 private lateinit var prev: Button
 private lateinit var next: Button

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(6,69,47); window.navigationBarColor = Color.rgb(6,69,47)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(6,69,47)) }
  val title = TextView(this).apply { text = "رشیدنامه"; textSize = 23f; setTextColor(Color.rgb(242,214,117)); gravity = Gravity.CENTER; setPadding(12,14,12,14) }
  root.addView(title, LinearLayout.LayoutParams(-1,-2))
  val frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(245,242,232)) }
  pageView = ZoomImageView(this).apply { scaleType = ImageView.ScaleType.MATRIX; setBackgroundColor(Color.WHITE) }
  frame.addView(pageView, FrameLayout.LayoutParams(-1,-1)); root.addView(frame, LinearLayout.LayoutParams(-1,0,1f))
  label = TextView(this).apply { text = "در حال آماده‌سازی کتاب…"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(8,8,8,8) }
  root.addView(label, LinearLayout.LayoutParams(-1,-2))
  val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(8,2,8,12) }
  prev = makeButton("صفحه قبل") { go(-1) }; next = makeButton("صفحه بعد") { go(1) }
  buttons.addView(prev, LinearLayout.LayoutParams(0,-2,1f)); buttons.addView(next, LinearLayout.LayoutParams(0,-2,1f)); root.addView(buttons)
  setContentView(root)
  pageView.swipeLeft = { go(1) }; pageView.swipeRight = { go(-1) }
  try {
   val file = File(cacheDir,"Rashidnameh.pdf")
   assets.open("Rashidnameh.pdf").use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
   descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
   renderer = PdfRenderer(descriptor!!)
   pageIndex = (savedInstanceState?.getInt("page") ?: 0).coerceIn(0, max(0, renderer!!.pageCount-1))
   showPage()
  } catch (e: Exception) { label.text = "باز کردن کتاب ناموفق بود: ${e.message}" }
 }
 private fun makeButton(textValue: String, action: () -> Unit) = Button(this).apply {
  text = textValue; setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(11,92,62)); setOnClickListener { action() }
 }
 private fun go(delta: Int) { val count = renderer?.pageCount ?: return; val target = (pageIndex+delta).coerceIn(0,count-1); if(target != pageIndex) { pageIndex=target; showPage() } }
 private fun showPage() {
  val pdf = renderer ?: return
  val page = pdf.openPage(pageIndex)
  try {
   val width = max(900, page.width*2); val height = max(1200, (width.toFloat()*page.height/page.width).toInt())
   val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE)
   page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
   pageView.setImageBitmap(bitmap); pageView.post { pageView.resetZoom() }
   label.text = "صفحه ${pageIndex+1} از ${pdf.pageCount}"
   prev.isEnabled = pageIndex>0; next.isEnabled = pageIndex<pdf.pageCount-1
   prev.alpha=if(pageIndex>0) 1f else .5f; next.alpha=if(pageIndex<pdf.pageCount-1) 1f else .5f
  } finally { page.close() }
 }
 override fun onSaveInstanceState(outState: Bundle) { outState.putInt("page",pageIndex); super.onSaveInstanceState(outState) }
 override fun onDestroy() { renderer?.close(); descriptor?.close(); super.onDestroy() }
}

private class ZoomImageView(context: android.content.Context) : ImageView(context) {
 var swipeLeft:(()->Unit)?=null; var swipeRight:(()->Unit)?=null
 private val matrixState=Matrix(); private var factor=1f; private var downX=0f; private var downY=0f; private var lastX=0f; private var lastY=0f; private var moved=false
 private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScale(d:ScaleGestureDetector):Boolean { val new=(factor*d.scaleFactor).coerceIn(1f,5f); matrixState.postScale(new/factor,new/factor,d.focusX,d.focusY); factor=new; imageMatrix=matrixState; return true }
 })
 fun resetZoom(){ factor=1f; matrixState.reset(); val dr=drawable?:return; val vw=width.toFloat().coerceAtLeast(1f); val vh=height.toFloat().coerceAtLeast(1f); val dw=dr.intrinsicWidth.toFloat().coerceAtLeast(1f); val dh=dr.intrinsicHeight.toFloat().coerceAtLeast(1f); val s=min(vw/dw,vh/dh); matrixState.setScale(s,s); matrixState.postTranslate((vw-dw*s)/2,(vh-dh*s)/2); imageMatrix=matrixState }
 override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){super.onSizeChanged(w,h,ow,oh); if(drawable!=null) resetZoom()}
 override fun onTouchEvent(e:MotionEvent):Boolean { scaleDetector.onTouchEvent(e); when(e.actionMasked){ MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;lastX=e.x;lastY=e.y;moved=false;return true}; MotionEvent.ACTION_MOVE->{if(e.pointerCount==1&&factor>1.02f){matrixState.postTranslate(e.x-lastX,e.y-lastY);imageMatrix=matrixState;moved=true};lastX=e.x;lastY=e.y;return true}; MotionEvent.ACTION_UP->{val dx=e.x-downX;val dy=e.y-downY;if(!moved&&factor<=1.02f&&abs(dx)>65&&abs(dx)>abs(dy)*1.2f){if(dx<0)swipeLeft?.invoke() else swipeRight?.invoke()};performClick();return true} };return true }
 override fun performClick():Boolean {super.performClick();return true}
}

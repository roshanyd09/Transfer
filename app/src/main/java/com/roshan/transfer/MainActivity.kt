package com.roshan.transfer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import android.graphics.Bitmap
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class MainActivity : Activity() {
    private var server: TransferServer? = null
    private lateinit var status: TextView
    private lateinit var address: TextView
    private lateinit var mascot: TextView
    private lateinit var connectButton: Button
    private lateinit var fileList: LinearLayout

    private val bg = Color.rgb(8,10,16)
    private val surface = Color.rgb(18,22,32)
    private val surface2 = Color.rgb(24,29,42)
    private val text = Color.rgb(245,247,255)
    private val muted = Color.rgb(155,165,186)
    private val accent = Color.rgb(139,108,255)
    private val green = Color.rgb(72,227,154)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestCameraIfNeeded()
        startServer()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,20,22,16); setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,0,0,20) }

        val brand = TextView(this).apply { text = "⚡ TRANSFER"; setTextColor(text); textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        content.addView(brand, lp(-1, -2))
        content.addView(TextView(this).apply { text = "Move. Share. Done.  ·  Made by Roshan"; setTextColor(muted); textSize = 14f }, lp(-1, -2, 0, 3, 0, 0))

        val hero = card()
        mascot = TextView(this).apply { text = "⚡"; textSize = 58f; gravity = Gravity.CENTER }
        hero.addView(mascot, lp(-1, 90))
        status = TextView(this).apply { text = "● Starting…"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(green) }
        hero.addView(status, lp(-1, -2))
        address = TextView(this).apply { text = ""; textSize = 15f; gravity = Gravity.CENTER; setTextColor(muted) }
        hero.addView(address, lp(-1, -2, 0, 5, 0, 0))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0,14,0,0) }
        val scan = button("▣  SCAN QR") { scanQr() }
        val copy = button("COPY IP") { copyIp() }
        val qr = button("SHOW QR") { showQr() }
        actions.addView(scan, lp(0, 52, 1f, 0, 0, 0, 7, 0))
        actions.addView(copy, lp(0, 52, 1f, 0, 7, 0, 7, 0))
        actions.addView(qr, lp(0, 52, 1f, 0, 7, 0, 0, 0))
        hero.addView(actions)
        connectButton = button("DISCONNECT") { stopServer() }
        hero.addView(connectButton, lp(-1, 50, 0, 12, 0, 0))
        content.addView(hero)

        val sendCard = card()
        sendCard.addView(sectionTitle("SEND FROM PHONE"))
        sendCard.addView(TextView(this).apply { text = "Choose original files. TRANSFER never resizes or recompresses them."; setTextColor(muted); textSize = 13f }, lp(-1,-2,0,0,0,12))
        sendCard.addView(button("📤  SELECT FILES") { pickFiles() }, lp(-1,52))
        content.addView(sendCard)

        val receiveCard = card()
        receiveCard.addView(sectionTitle("RECEIVED FILES"))
        fileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        receiveCard.addView(fileList, lp(-1,-2))
        content.addView(receiveCard)

        val more = card()
        more.addView(sectionTitle("ABOUT"))
        more.addView(TextView(this).apply { text = "Local Wi-Fi / hotspot transfer
No cloud • No compression • No account
Version 1.1"; setTextColor(muted); textSize=13f }, lp(-1,-2))
        content.addView(more)

        scroll.addView(content)
        root.addView(scroll, lp(-1,0,1f))
        setContentView(root)
    }

    private fun startServer() {
        try {
            if (server?.isAlive == true) return
            server = TransferServer(this).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            val ip = getIp()
            status.text = if (ip == "Not connected") "● Waiting for network" else "● READY — Connected"
            address.text = if (ip == "Not connected") "Connect to Wi-Fi or hotspot" else "http://$ip:${server!!.listeningPort}"
            connectButton.text = "DISCONNECT"
            loadFiles()
        } catch (e: Exception) {
            status.text = "● Server error"
            address.text = e.message ?: "Unable to start"
        }
    }

    private fun stopServer() {
        server?.stop(); server = null
        status.text = "● DISCONNECTED"; status.setTextColor(Color.rgb(255,100,124)); address.text = "Tap CONNECT to start again"
        connectButton.text = "CONNECT"
        connectButton.setOnClickListener { startServer(); connectButton.setOnClickListener { stopServer() } }
    }

    private fun getIp(): String {
        return try {
            val e = NetworkInterface.getNetworkInterfaces()
            while (e.hasMoreElements()) { val n=e.nextElement(); val a=n.inetAddresses
                while(a.hasMoreElements()){ val x=a.nextElement(); if(!x.isLoopbackAddress && x is Inet4Address && x.hostAddress?.startsWith("169.254") != true) return x.hostAddress ?: "" }
            }
            "Not connected"
        } catch(_:Exception) { "Not connected" }
    }

    private fun copyIp() {
        val v = address.text.toString(); if (v.startsWith("http")) { val cm=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("TRANSFER address",v)); Toast.makeText(this,"Address copied",Toast.LENGTH_SHORT).show() }
    }

    private fun scanQr() {
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("Scan a TRANSFER QR code")
        integrator.setBeepEnabled(true)
        integrator.setOrientationLocked(false)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        if (requestCode == 400 && resultCode == RESULT_OK && data != null) {
            val uris = mutableListOf<Uri>()
            data.data?.let { uris.add(it) }
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            if (uris.isNotEmpty()) {
                var copied = 0
                uris.forEach { uri ->
                    try {
                        val name = queryName(uri) ?: "file_${System.currentTimeMillis()}"
                        val target = server?.importFromUri(uri, name)
                        if (target != null) copied++
                    } catch (_: Exception) {}
                }
                Toast.makeText(this, "$copied file(s) ready on your PC browser", Toast.LENGTH_LONG).show()
                loadFiles()
            }
            return
        }
        val r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data)
        if(r!=null && r.contents!=null){ val u=r.contents; if(u.startsWith("http")){ Toast.makeText(this,"Scanned: $u",Toast.LENGTH_SHORT).show(); try{startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))}catch(_:Exception){} } else Toast.makeText(this,"QR does not contain a TRANSFER address",Toast.LENGTH_SHORT).show(); return }
        super.onActivityResult(requestCode,resultCode,data)
    }

    private fun pickFiles(){
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)}
        startActivityForResult(i, 400)
    }


    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment
    }

    private fun showQr() {
        val ip = getIp(); val s = server ?: return
        if (ip == "Not connected") { Toast.makeText(this, "Connect to Wi-Fi or hotspot first", Toast.LENGTH_SHORT).show(); return }
        val url = "http://$ip:${s.listeningPort}"
        try {
            val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 760, 760)
            val bmp = Bitmap.createBitmap(760, 760, Bitmap.Config.ARGB_8888)
            for (x in 0 until 760) for (y in 0 until 760) bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            val iv = ImageView(this).apply { setImageBitmap(bmp); setPadding(24,24,24,24) }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,20,24,20); setBackgroundColor(Color.WHITE) }
            box.addView(iv, LinearLayout.LayoutParams(-1, 0, 1f))
            box.addView(TextView(this).apply { text = url; setTextColor(Color.BLACK); gravity = Gravity.CENTER; textSize = 14f; setPadding(0,8,0,8) }, LinearLayout.LayoutParams(-1,-2))
            android.app.AlertDialog.Builder(this).setTitle("Scan to connect").setView(box).setPositiveButton("Done", null).show()
        } catch (e: Exception) { Toast.makeText(this, "QR error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun loadFiles(){
        fileList.removeAllViews(); val files=server?.localFiles().orEmpty()
        if(files.isEmpty()){ fileList.addView(TextView(this).apply{text="No files yet. Files uploaded from your PC will appear here.";setTextColor(muted);textSize=13f;setPadding(0,6,0,4)}) ; return }
        files.take(30).forEach{f-> val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,7,0,7)}
            val t=TextView(this).apply{text="📄 ${f.name}\n${size(f.length())}";setTextColor(text);textSize=14f}
            row.addView(t,lp(0,-2,1f)); val del=button("DELETE"){f.delete();loadFiles()};row.addView(del,lp(92,44));fileList.addView(row)}
    }

    private fun sectionTitle(s:String)=TextView(this).apply{text=s;setTextColor(text);textSize=13f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setPadding(0,0,0,10)}
    private fun card():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18);background=rounded(surface,22);elevation=5f;layoutParams=lp(-1,-2,0,0,0,14)}
    private fun button(s:String,action:()->Unit)=Button(this).apply{text=s;setTextColor(text);textSize=12f;isAllCaps=false;setOnClickListener{action()};background=rounded(surface2,15);stateListAnimator=null}
    private fun rounded(color:Int,r:Float)=GradientDrawable().apply{setColor(color);cornerRadius=r}
    private fun lp(w:Int,h:Int,weight:Float=0f,l:Int=0,t:Int=0,r:Int=0,b:Int=0)=LinearLayout.LayoutParams(w,h,weight).apply{setMargins(l,t,r,b)}
    private fun size(n:Long):String{var x=n.toDouble();val u=arrayOf("B","KB","MB","GB","TB");var i=0;while(x>=1024&&i<u.lastIndex){x/=1024;i++};return String.format(Locale.US,"%.1f %s",x,u[i])}

    override fun onResume(){super.onResume();if(::fileList.isInitialized)loadFiles()}
    private fun requestCameraIfNeeded(){if(android.os.Build.VERSION.SDK_INT>=23 && ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),99)}
}

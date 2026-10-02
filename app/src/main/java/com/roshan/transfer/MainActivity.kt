package com.roshan.transfer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator

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

    private val bg = Color.rgb(8, 10, 16)
    private val surface = Color.rgb(18, 22, 32)
    private val surface2 = Color.rgb(24, 29, 42)
    private val text = Color.rgb(245, 247, 255)
    private val muted = Color.rgb(155, 165, 186)
    private val green = Color.rgb(72, 227, 154)
    private val red = Color.rgb(255, 100, 124)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        requestCameraIfNeeded()
        startServer()
    }


    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 16)
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 20)
        }


        // ---------------- BRAND ----------------

        val brand = TextView(this).apply {
            this.text = "⚡ TRANSFER"
            setTextColor(this@MainActivity.text)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        content.addView(
            brand,
            lp(-1, -2)
        )


        val tagline = TextView(this).apply {
            this.text = "Move. Share. Done. · Made by Roshan"
            setTextColor(muted)
            textSize = 14f
        }

        content.addView(
            tagline,
            lp(-1, -2, 0f, 3, 0, 0, 0)
        )


        // ---------------- HERO CARD ----------------

        val hero = card()


        mascot = TextView(this).apply {
            text = "⚡"
            textSize = 58f
            gravity = Gravity.CENTER
        }

        hero.addView(
            mascot,
            lp(-1, 90)
        )


        status = TextView(this).apply {
            text = "● Starting…"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(green)
        }

        hero.addView(
            status,
            lp(-1, -2)
        )


        address = TextView(this).apply {
            text = ""
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        hero.addView(
            address,
            lp(-1, -2, 0f, 5, 0, 0, 0)
        )


        // ---------------- ACTION BUTTONS ----------------

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 0)
        }


        val scan = button("▣ SCAN QR") {
            scanQr()
        }

        val copy = button("COPY IP") {
            copyIp()
        }

        val qr = button("SHOW QR") {
            showQr()
        }


        actions.addView(
            scan,
            lp(0, 52, 1f, 0, 0, 0, 7)
        )

        actions.addView(
            copy,
            lp(0, 52, 1f, 0, 7, 0, 7)
        )

        actions.addView(
            qr,
            lp(0, 52, 1f, 0, 7, 0, 0)
        )


        hero.addView(actions)


        // ---------------- CONNECT BUTTON ----------------

        connectButton = button("DISCONNECT") {
            stopServer()
        }

        hero.addView(
            connectButton,
            lp(-1, 50, 0f, 12, 0, 0, 0)
        )


        content.addView(hero)


        // ---------------- SEND CARD ----------------

        val sendCard = card()

        sendCard.addView(
            sectionTitle("SEND FROM PHONE")
        )


        val sendInfo = TextView(this).apply {
            text =
                "Choose original files. TRANSFER never resizes or recompresses them."
            setTextColor(muted)
            textSize = 13f
        }

        sendCard.addView(
            sendInfo,
            lp(-1, -2, 0f, 0, 0, 12, 0)
        )


        sendCard.addView(
            button("📤 SELECT FILES") {
                pickFiles()
            },
            lp(-1, 52)
        )


        content.addView(sendCard)


        // ---------------- RECEIVED FILES ----------------

        val receiveCard = card()

        receiveCard.addView(
            sectionTitle("RECEIVED FILES")
        )


        fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }


        receiveCard.addView(
            fileList,
            lp(-1, -2)
        )


        content.addView(receiveCard)


        // ---------------- ABOUT ----------------

        val more = card()

        more.addView(
            sectionTitle("ABOUT")
        )


        val aboutText = TextView(this).apply {
            text =
                "Local Wi-Fi / hotspot transfer\n\n" +
                "No cloud • No compression • No account\n\n" +
                "Version 1.1"

            setTextColor(muted)
            textSize = 13f
        }


        more.addView(
            aboutText,
            lp(-1, -2)
        )


        content.addView(more)


        // ---------------- FINAL UI ----------------

        scroll.addView(content)

        root.addView(
            scroll,
            lp(-1, 0, 1f)
        )

        setContentView(root)
    }


    // =========================================================
    // SERVER
    // =========================================================

    private fun startServer() {

        try {

            if (server?.isAlive == true) {
                return
            }


            server = TransferServer(this).also {
                it.start(
                    NanoHTTPD.SOCKET_READ_TIMEOUT,
                    false
                )
            }


            val ip = getIp()


            if (ip == "Not connected") {

                status.text = "● Waiting for network"
                status.setTextColor(Color.rgb(255, 190, 70))

                address.text = "Connect to Wi-Fi or hotspot"

            } else {

                status.text = "● READY — Connected"
                status.setTextColor(green)

                address.text =
                    "http://$ip:${server!!.listeningPort}"
            }


            connectButton.text = "DISCONNECT"

            connectButton.setOnClickListener {
                stopServer()
            }


            loadFiles()


        } catch (e: Exception) {

            status.text = "● Server error"
            status.setTextColor(red)

            address.text =
                e.message ?: "Unable to start"
        }
    }


    private fun stopServer() {

        server?.stop()
        server = null


        status.text = "● DISCONNECTED"
        status.setTextColor(red)

        address.text =
            "Tap CONNECT to start again"


        connectButton.text = "CONNECT"


        connectButton.setOnClickListener {
            startServer()
        }
    }


    // =========================================================
    // FIND PHONE IP
    // =========================================================

    private fun getIp(): String {

        return try {

            val interfaces =
                NetworkInterface.getNetworkInterfaces()


            while (interfaces.hasMoreElements()) {

                val network =
                    interfaces.nextElement()

                val addresses =
                    network.inetAddresses


                while (addresses.hasMoreElements()) {

                    val addr =
                        addresses.nextElement()


                    if (
                        !addr.isLoopbackAddress &&
                        addr is Inet4Address &&
                        addr.hostAddress?.startsWith("169.254") != true
                    ) {

                        return addr.hostAddress ?: ""
                    }
                }
            }


            "Not connected"

        } catch (_: Exception) {

            "Not connected"
        }
    }


    // =========================================================
    // COPY IP
    // =========================================================

    private fun copyIp() {

        val value =
            address.text.toString()


        if (value.startsWith("http")) {

            val clipboard =
                getSystemService(CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager


            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "TRANSFER address",
                    value
                )
            )


            Toast.makeText(
                this,
                "Address copied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =========================================================
    // QR SCANNER
    // =========================================================

    private fun scanQr() {

        val integrator =
            IntentIntegrator(this)


        integrator.setPrompt(
            "Scan a TRANSFER QR code"
        )

        integrator.setBeepEnabled(true)

        integrator.setOrientationLocked(false)

        integrator.setDesiredBarcodeFormats(
            IntentIntegrator.QR_CODE
        )

        integrator.initiateScan()
    }


    // =========================================================
    // ACTIVITY RESULT
    // =========================================================

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        // ---------------- FILE PICKER ----------------

        if (
            requestCode == 400 &&
            resultCode == RESULT_OK &&
            data != null
        ) {

            val uris =
                mutableListOf<Uri>()


            data.data?.let {
                uris.add(it)
            }


            data.clipData?.let { clip ->

                for (i in 0 until clip.itemCount) {

                    uris.add(
                        clip.getItemAt(i).uri
                    )
                }
            }


            if (uris.isNotEmpty()) {

                var copied = 0


                uris.forEach { uri ->

                    try {

                        val name =
                            queryName(uri)
                                ?: "file_${System.currentTimeMillis()}"


                        val target =
                            server?.importFromUri(
                                uri,
                                name
                            )


                        if (target != null) {
                            copied++
                        }

                    } catch (_: Exception) {
                    }
                }


                Toast.makeText(
                    this,
                    "$copied file(s) ready on your PC browser",
                    Toast.LENGTH_LONG
                ).show()


                loadFiles()
            }


            return
        }


        // ---------------- QR RESULT ----------------

        val result =
            IntentIntegrator.parseActivityResult(
                requestCode,
                resultCode,
                data
            )


        if (
            result != null &&
            result.contents != null
        ) {

            val scanned =
                result.contents


            if (scanned.startsWith("http")) {

                Toast.makeText(
                    this,
                    "Scanned: $scanned",
                    Toast.LENGTH_SHORT
                ).show()


                try {

                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(scanned)
                        )
                    )

                } catch (_: Exception) {
                }

            } else {

                Toast.makeText(
                    this,
                    "QR does not contain a TRANSFER address",
                    Toast.LENGTH_SHORT
                ).show()
            }


            return
        }


        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    }


    // =========================================================
    // PICK FILES
    // =========================================================

    private fun pickFiles() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {

                type = "*/*"

                putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    true
                )

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
            }


        startActivityForResult(
            intent,
            400
        )
    }


    // =========================================================
    // GET FILE NAME
    // =========================================================

    private fun queryName(
        uri: Uri
    ): String? {

        contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->

            val index =
                cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )


            if (
                index >= 0 &&
                cursor.moveToFirst()
            ) {

                return cursor.getString(index)
            }
        }


        return uri.lastPathSegment
    }


    // =========================================================
    // SHOW QR
    // =========================================================

    private fun showQr() {

        val ip =
            getIp()

        val currentServer =
            server


        if (currentServer == null) {

            Toast.makeText(
                this,
                "Server is not running",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (ip == "Not connected") {

            Toast.makeText(
                this,
                "Connect to Wi-Fi or hotspot first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val url =
            "http://$ip:${currentServer.listeningPort}"


        try {

            val matrix =
                MultiFormatWriter().encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    760,
                    760
                )


            val bitmap =
                Bitmap.createBitmap(
                    760,
                    760,
                    Bitmap.Config.ARGB_8888
                )


            for (x in 0 until 760) {

                for (y in 0 until 760) {

                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix.get(x, y))
                            Color.BLACK
                        else
                            Color.WHITE
                    )
                }
            }


            val imageView =
                ImageView(this).apply {

                    setImageBitmap(bitmap)

                    setPadding(
                        24,
                        24,
                        24,
                        24
                    )
                }


            val box =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    setPadding(
                        24,
                        20,
                        24,
                        20
                    )

                    setBackgroundColor(
                        Color.WHITE
                    )
                }


            box.addView(
                imageView,
                LinearLayout.LayoutParams(
                    -1,
                    0,
                    1f
                )
            )


            val urlText =
                TextView(this).apply {

                    text = url

                    setTextColor(
                        Color.BLACK
                    )

                    gravity =
                        Gravity.CENTER

                    textSize = 14f

                    setPadding(
                        0,
                        8,
                        0,
                        8
                    )
                }


            box.addView(
                urlText,
                LinearLayout.LayoutParams(
                    -1,
                    -2
                )
            )


            android.app.AlertDialog.Builder(this)
                .setTitle("Scan to connect")
                .setView(box)
                .setPositiveButton(
                    "Done",
                    null
                )
                .show()


        } catch (e: Exception) {

            Toast.makeText(
                this,
                "QR error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =========================================================
    // LOAD RECEIVED FILES
    // =========================================================

    private fun loadFiles() {

        if (!::fileList.isInitialized) {
            return
        }


        fileList.removeAllViews()


        val files =
            server?.localFiles().orEmpty()


        if (files.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text =
                        "No files yet. Files uploaded from your PC will appear here."

                    setTextColor(muted)

                    textSize = 13f

                    setPadding(
                        0,
                        6,
                        0,
                        4
                    )
                }


            fileList.addView(empty)

            return
        }


        files
            .take(30)
            .forEach { file ->

                val row =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.HORIZONTAL

                        gravity =
                            Gravity.CENTER_VERTICAL

                        setPadding(
                            0,
                            7,
                            0,
                            7
                        )
                    }


                val fileText =
                    TextView(this).apply {

                        text =
                            "📄 ${file.name}\n${size(file.length())}"

                        setTextColor(
                            this@MainActivity.text
                        )

                        textSize = 14f
                    }


                row.addView(
                    fileText,
                    lp(
                        0,
                        -2,
                        1f
                    )
                )


                val deleteButton =
                    button("DELETE") {

                        file.delete()

                        loadFiles()
                    }


                row.addView(
                    deleteButton,
                    lp(
                        92,
                        44
                    )
                )


                fileList.addView(row)
            }
    }


    // =========================================================
    // SECTION TITLE
    // =========================================================

    private fun sectionTitle(
        value: String
    ): TextView {

        return TextView(this).apply {

            text = value

            setTextColor(
                this@MainActivity.text
            )

            textSize = 13f

            typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            setPadding(
                0,
                0,
                0,
                10
            )
        }
    }


    // =========================================================
    // CARD
    // =========================================================

    private fun card(): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                18,
                18,
                18,
                18
            )

            background =
                rounded(
                    surface,
                    22f
                )

            elevation = 5f

            layoutParams =
                lp(
                    -1,
                    -2,
                    0f,
                    0,
                    0,
                    0,
                    14
                )
        }
    }


    // =========================================================
    // BUTTON
    // =========================================================

    private fun button(
        label: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            text = label

            setTextColor(
                this@MainActivity.text
            )

            textSize = 12f

            isAllCaps = false

            setOnClickListener {
                action()
            }

            background =
                rounded(
                    surface2,
                    15f
                )

            stateListAnimator = null
        }
    }


    // =========================================================
    // ROUNDED BACKGROUND
    // =========================================================

    private fun rounded(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius = radius
        }
    }


    // =========================================================
    // LAYOUT PARAMS
    // =========================================================

    private fun lp(
        width: Int,
        height: Int,
        weight: Float = 0f,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            width,
            height,
            weight
        ).apply {

            setMargins(
                left,
                top,
                right,
                bottom
            )
        }
    }


    // =========================================================
    // FILE SIZE
    // =========================================================

    private fun size(
        bytes: Long
    ): String {

        var value =
            bytes.toDouble()

        val units =
            arrayOf(
                "B",
                "KB",
                "MB",
                "GB",
                "TB"
            )

        var index = 0


        while (
            value >= 1024 &&
            index < units.lastIndex
        ) {

            value /= 1024

            index++
        }


        return String.format(
            Locale.US,
            "%.1f %s",
            value,
            units[index]
        )
    }


    // =========================================================
    // RESUME
    // =========================================================

    override fun onResume() {

        super.onResume()

        if (::fileList.isInitialized) {
            loadFiles()
        }
    }


    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        server?.stop()
        server = null

        super.onDestroy()
    }


    // =========================================================
    // CAMERA PERMISSION
    // =========================================================

    private fun requestCameraIfNeeded() {

        if (
            android.os.Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                99
            )
        }
    }
}

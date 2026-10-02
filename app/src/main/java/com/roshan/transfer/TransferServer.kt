package com.roshan.transfer

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.ServerSocket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TransferServer(
    private val ctx: Context
) : NanoHTTPD(findFreePort()) {

    private val dir = File(
        ctx.filesDir,
        "transfers"
    ).apply {
        mkdirs()
    }


    companion object {

        fun findFreePort(): Int {
            ServerSocket(0).use {
                return it.localPort
            }
        }
    }


    // =========================================================
    // LOCAL FILES
    // =========================================================

    fun localFiles(): List<File> {

        return dir
            .listFiles()
            ?.filter {
                it.isFile
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            ?: emptyList()
    }


    // =========================================================
    // IMPORT FILE FROM PHONE
    // =========================================================

    fun importFromUri(
        uri: android.net.Uri,
        requestedName: String
    ): File {

        val target =
            uniqueFile(
                File(
                    dir,
                    safeName(requestedName)
                )
            )


        ctx.contentResolver
            .openInputStream(uri)
            .use { input ->

                requireNotNull(input) {
                    "Unable to read selected file"
                }


                target
                    .outputStream()
                    .use { output ->

                        input.copyTo(
                            output,
                            1024 * 64
                        )
                    }
            }


        return target
    }


    // =========================================================
    // HTTP SERVER
    // =========================================================

    override fun serve(
        session: IHTTPSession
    ): Response {

        return try {

            when (session.method) {

                // -------------------------------------------------
                // GET
                // -------------------------------------------------

                Method.GET -> {

                    when {

                        session.uri == "/api/info" -> {

                            json(
                                """{"name":"TRANSFER","version":"1.1","quality":"original"}"""
                            )
                        }


                        session.uri == "/api/files" -> {

                            val body =
                                localFiles()
                                    .joinToString(
                                        ",",
                                        "[",
                                        "]"
                                    ) { file ->

                                        val name =
                                            file.name
                                                .replace(
                                                    "\\",
                                                    "\\\\"
                                                )
                                                .replace(
                                                    "\"",
                                                    "\\\""
                                                )


                                        """{"name":"$name","size":${file.length()},"modified":${file.lastModified()}}"""
                                    }


                            json(body)
                        }


                        session.uri == "/download" -> {

                            download(session)
                        }


                        else -> {

                            newFixedLengthResponse(
                                Response.Status.OK,
                                "text/html; charset=utf-8",
                                desktopHtml()
                            )
                        }
                    }
                }


                // -------------------------------------------------
                // POST
                // -------------------------------------------------

                Method.POST -> {

                    when {

                        session.uri == "/upload" -> {

                            upload(session)
                        }


                        session.uri == "/delete" -> {

                            delete(session)
                        }


                        else -> {

                            newFixedLengthResponse(
                                Response.Status.NOT_FOUND,
                                "text/plain",
                                "Not found"
                            )
                        }
                    }
                }


                else -> {

                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        "text/plain",
                        "Method not allowed"
                    )
                }
            }


        } catch (e: Exception) {

            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                e.message ?: "Transfer error"
            )
        }
    }


    // =========================================================
    // UPLOAD FROM PC
    // =========================================================

    private fun upload(
        session: IHTTPSession
    ): Response {

        val name =
            safeName(
                session.headers["x-file-name"]
                    ?: "file_${System.currentTimeMillis()}"
            )


        val length =
            session.headers["content-length"]
                ?.toLongOrNull()
                ?: -1L


        val target =
            uniqueFile(
                File(
                    dir,
                    name
                )
            )


        target
            .outputStream()
            .use { output ->

                session.inputStream.copyTo(
                    output,
                    1024 * 64
                )
            }


        return json(
            """{"ok":true,"name":"${target.name.jsonEscape()}","size":${target.length()},"expected":$length}"""
        )
    }


    // =========================================================
    // DOWNLOAD TO PC
    // =========================================================

    private fun download(
        session: IHTTPSession
    ): Response {

        val raw =
            session.parameters["name"]
                ?.firstOrNull()
                ?: return bad("Missing file")


        val file =
            File(
                dir,
                safeName(raw)
            )


        if (
            !file.exists() ||
            !file.isFile
        ) {

            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not found"
            )
        }


        return newChunkedResponse(
            Response.Status.OK,
            contentType(file.name),
            file.inputStream()
        ).apply {

            addHeader(
                "Content-Disposition",
                "attachment; filename*=UTF-8''${
                    URLEncoder
                        .encode(
                            file.name,
                            StandardCharsets.UTF_8
                        )
                        .replace(
                            "+",
                            "%20"
                        )
                }"
            )


            addHeader(
                "Content-Length",
                file.length().toString()
            )


            addHeader(
                "Cache-Control",
                "no-store"
            )
        }
    }


    // =========================================================
    // DELETE
    // =========================================================

    private fun delete(
        session: IHTTPSession
    ): Response {

        val raw =
            session.parameters["name"]
                ?.firstOrNull()
                ?: return bad("Missing file")


        val file =
            File(
                dir,
                safeName(raw)
            )


        return json(
            """{"ok":${file.delete()}}"""
        )
    }


    // =========================================================
    // JSON RESPONSE
    // =========================================================

    private fun json(
        body: String
    ): Response {

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            body
        ).apply {

            addHeader(
                "Access-Control-Allow-Origin",
                "*"
            )


            addHeader(
                "Cache-Control",
                "no-store"
            )
        }
    }


    // =========================================================
    // BAD REQUEST
    // =========================================================

    private fun bad(
        message: String
    ): Response {

        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "text/plain",
            message
        )
    }


    // =========================================================
    // SAFE FILE NAME
    // =========================================================

    private fun safeName(
        name: String
    ): String {

        return File(
            name.replace(
                '\u0000'.toString(),
                ""
            )
        )
            .name
            .ifBlank {
                "file"
            }
    }


    // =========================================================
    // UNIQUE FILE
    // =========================================================

    private fun uniqueFile(
        original: File
    ): File {

        if (!original.exists()) {
            return original
        }


        val base =
            original.nameWithoutExtension


        val extension =
            original.extension


        var index = 1


        while (true) {

            val newName =
                if (extension.isBlank()) {

                    "$base ($index)"

                } else {

                    "$base ($index).$extension"
                }


            val candidate =
                File(
                    original.parentFile,
                    newName
                )


            if (!candidate.exists()) {
                return candidate
            }


            index++
        }
    }


    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private fun String.jsonEscape(): String {

        return replace(
            "\\",
            "\\\\"
        ).replace(
            "\"",
            "\\\""
        )
    }


    // =========================================================
    // CONTENT TYPE
    // =========================================================

    private fun contentType(
        name: String
    ): String {

        return when (
            name.substringAfterLast(
                ".",
                ""
            ).lowercase()
        ) {

            "jpg",
            "jpeg" ->
                "image/jpeg"

            "png" ->
                "image/png"

            "gif" ->
                "image/gif"

            "webp" ->
                "image/webp"

            "mp4" ->
                "video/mp4"

            "mkv" ->
                "video/x-matroska"

            "mp3" ->
                "audio/mpeg"

            "wav" ->
                "audio/wav"

            "pdf" ->
                "application/pdf"

            "txt" ->
                "text/plain"

            "html" ->
                "text/html"

            "zip" ->
                "application/zip"

            else ->
                "application/octet-stream"
        }
    }


    // =========================================================
    // DESKTOP WEB PAGE
    // =========================================================

    private fun desktopHtml(): String {

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">

<meta
    name="viewport"
    content="width=device-width, initial-scale=1.0"
>

<title>TRANSFER — Made by Roshan</title>

<style>

body {
    margin: 0;
    padding: 0;
    background: #080a10;
    color: #f5f7ff;
    font-family: Arial, sans-serif;
}

.container {
    max-width: 900px;
    margin: auto;
    padding: 30px;
}

.card {
    background: #121620;
    border-radius: 22px;
    padding: 24px;
    margin-bottom: 20px;
}

h1 {
    margin-bottom: 5px;
}

.muted {
    color: #9ba5ba;
}

input[type=file] {
    margin: 15px 0;
}

button {
    background: #181d2a;
    color: white;
    border: none;
    padding: 12px 18px;
    border-radius: 12px;
    cursor: pointer;
}

button:hover {
    opacity: 0.8;
}

.file {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14px 0;
    border-bottom: 1px solid #292f3d;
}

.progress {
    width: 100%;
    height: 8px;
    background: #242a38;
    border-radius: 10px;
    margin-top: 12px;
}

.bar {
    height: 100%;
    width: 0%;
    background: #48e39a;
    border-radius: 10px;
}

</style>

</head>

<body>

<div class="container">

<div class="card">

<h1>⚡ TRANSFER</h1>

<div class="muted">
Move. Share. Done. · Made by Roshan
</div>

</div>


<div class="card">

<h2>📤 Send files to phone</h2>

<input
    id="fileInput"
    type="file"
    multiple
>

<br>

<button onclick="uploadFiles()">
Upload Files
</button>

<div class="progress">
<div
    id="bar"
    class="bar"
></div>
</div>

<p
    id="status"
    class="muted"
></p>

</div>


<div class="card">

<h2>📁 Files on phone</h2>

<div id="files">
Loading...
</div>

</div>

</div>


<script>

async function loadFiles() {

    const response =
        await fetch('/api/files');

    const files =
        await response.json();

    const container =
        document.getElementById('files');

    container.innerHTML = '';

    if (files.length === 0) {

        container.innerHTML =
            '<p class="muted">No files yet.</p>';

        return;
    }


    files.forEach(file => {

        const row =
            document.createElement('div');

        row.className = 'file';


        const info =
            document.createElement('div');

        info.innerHTML =
            '<b>' +
            escapeHtml(file.name) +
            '</b><br>' +
            '<span class="muted">' +
            formatSize(file.size) +
            '</span>';


        const actions =
            document.createElement('div');


        const download =
            document.createElement('button');

        download.innerText =
            'DOWNLOAD';

        download.onclick = function() {

            window.location =
                '/download?name=' +
                encodeURIComponent(file.name);
        };


        const del =
            document.createElement('button');

        del.innerText =
            'DELETE';

        del.style.marginLeft =
            '8px';


        del.onclick = async function() {

            await fetch(
                '/delete?name=' +
                encodeURIComponent(file.name),
                {
                    method: 'POST'
                }
            );

            loadFiles();
        };


        actions.appendChild(download);
        actions.appendChild(del);

        row.appendChild(info);
        row.appendChild(actions);

        container.appendChild(row);
    });
}


async function uploadFiles() {

    const input =
        document.getElementById('fileInput');

    const files =
        input.files;

    if (!files.length) {

        alert(
            'Please select files first.'
        );

        return;
    }


    const bar =
        document.getElementById('bar');

    const status =
        document.getElementById('status');


    for (
        let i = 0;
        i < files.length;
        i++
    ) {

        const file =
            files[i];


        status.innerText =
            'Uploading ' +
            (i + 1) +
            ' / ' +
            files.length +
            ': ' +
            file.name;


        await uploadSingle(
            file,
            bar
        );
    }


    bar.style.width =
        '100%';


    status.innerText =
        'All files uploaded successfully.';


    loadFiles();
}


function uploadSingle(
    file,
    bar
) {

    return new Promise(
        function(resolve, reject) {

            const xhr =
                new XMLHttpRequest();


            xhr.open(
                'POST',
                '/upload'
            );


            xhr.setRequestHeader(
                'X-File-Name',
                encodeURIComponent(
                    file.name
                )
            );


            xhr.upload.onprogress =
                function(event) {

                    if (event.lengthComputable) {

                        const percent =
                            (
                                event.loaded /
                                event.total
                            ) * 100;

                        bar.style.width =
                            percent + '%';
                    }
                };


            xhr.onload =
                function() {

                    if (
                        xhr.status >= 200 &&
                        xhr.status < 300
                    ) {

                        resolve();

                    } else {

                        reject(
                            new Error(
                                'Upload failed'
                            )
                        );
                    }
                };


            xhr.onerror =
                function() {

                    reject(
                        new Error(
                            'Network error'
                        )
                    );
                };


            xhr.send(file);
        }
    );
}


function formatSize(bytes) {

    const units =
        [
            'B',
            'KB',
            'MB',
            'GB',
            'TB'
        ];


    let i = 0;


    while (
        bytes >= 1024 &&
        i < units.length - 1
    ) {

        bytes /= 1024;
        i++;
    }


    return (
        bytes.toFixed(1) +
        ' ' +
        units[i]
    );
}


function escapeHtml(value) {

    return value
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}


loadFiles();

</script>

</body>
</html>
""".trimIndent()
    }
}

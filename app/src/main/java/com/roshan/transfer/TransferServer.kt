package com.roshan.transfer

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.ServerSocket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TransferServer(private val ctx: Context) : NanoHTTPD(findFreePort()) {
    private val dir = File(ctx.filesDir, "transfers").apply { mkdirs() }

    companion object {
        fun findFreePort(): Int {
            ServerSocket(0).use { return it.localPort }
        }
    }

    fun localFiles(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun importFromUri(uri: android.net.Uri, requestedName: String): File {
        val target = uniqueFile(File(dir, safeName(requestedName)))
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read selected file" }
            target.outputStream().use { out -> input.copyTo(out, 1024 * 64) }
        }
        return target
    }


    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> when {
                    session.uri == "/api/info" -> json("{"name":"TRANSFER","version":"1.1","quality":"original"}")
                    session.uri == "/api/files" -> {
                        val body = localFiles().joinToString(",", "[", "]") {
                            val n = it.name.replace("\", "\\").replace(""", "\"")
                            "{"name":"$n","size":${it.length()},"modified":${it.lastModified()}}"
                        }
                        json(body)
                    }
                    session.uri == "/download" -> download(session)
                    else -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", desktopHtml())
                }
                Method.POST -> when {
                    session.uri == "/upload" -> upload(session)
                    session.uri == "/delete" -> delete(session)
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Transfer error")
        }
    }

    private fun upload(s: IHTTPSession): Response {
        val name = safeName(s.headers["x-file-name"] ?: "file_${System.currentTimeMillis()}")
        val length = s.headers["content-length"]?.toLongOrNull() ?: -1L
        val target = uniqueFile(File(dir, name))
        target.outputStream().use { out ->
            val input = s.inputStream
            input.copyTo(out, 1024 * 64)
        }
        return json("{"ok":true,"name":"${target.name.jsonEscape()}","size":${target.length()},"expected":$length}")
    }

    private fun download(s: IHTTPSession): Response {
        val raw = s.parameters["name"]?.firstOrNull() ?: return bad("Missing file")
        val file = File(dir, safeName(raw))
        if (!file.exists() || !file.isFile) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        return newChunkedResponse(Response.Status.OK, contentType(file.name), file.inputStream()).apply {
            addHeader("Content-Disposition", "attachment; filename*=UTF-8''${URLEncoder.encode(file.name, StandardCharsets.UTF_8).replace("+", "%20")}")
            addHeader("Content-Length", file.length().toString())
            addHeader("Cache-Control", "no-store")
        }
    }

    private fun delete(s: IHTTPSession): Response {
        val raw = s.parameters["name"]?.firstOrNull() ?: return bad("Missing file")
        val f = File(dir, safeName(raw))
        return json("{"ok":${f.delete()}}")
    }

    private fun json(body: String) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body).apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Cache-Control", "no-store")
    }
    private fun bad(message: String) = newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", message)
    private fun safeName(name: String) = File(name.replace('\u0000'.toString(), "")).name.ifBlank { "file" }
    private fun uniqueFile(base: File): File {
        if (!base.exists()) return base
        val stem = base.nameWithoutExtension
        val ext = if (base.extension.isBlank()) "" else ".${base.extension}"
        var i = 2
        var f = File(base.parentFile, "$stem ($i)$ext")
        while (f.exists()) f = File(base.parentFile, "$stem (${i++})$ext")
        return f
    }
    private fun contentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif";
        "mp4" -> "video/mp4"; "mov" -> "video/quicktime"; "pdf" -> "application/pdf"; "txt" -> "text/plain";
        else -> "application/octet-stream"
    }
    private fun String.jsonEscape() = replace("\", "\\").replace(""", "\"")

    private fun desktopHtml() = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>TRANSFER</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#080a10;color:#f5f7ff;font-family:Inter,system-ui,Arial;padding:28px}main{max-width:1040px;margin:auto}.top{display:flex;justify-content:space-between;align-items:center;margin-bottom:22px}.brand{font-size:28px;font-weight:800}.muted{color:#9ba5ba}.pill{padding:8px 12px;border:1px solid #2a3142;border-radius:999px;background:#121620}.card{background:#121620;border:1px solid #2a3142;border-radius:26px;padding:26px;margin-bottom:18px;box-shadow:0 18px 60px #0007}.hero{display:grid;grid-template-columns:1fr 1fr;gap:18px}.drop{min-height:260px;border:2px dashed #3b455d;border-radius:22px;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#0e121b;text-align:center}.mascot{font-size:64px;animation:b 1.5s ease-in-out infinite}@keyframes b{50%{transform:translateY(-10px) rotate(5deg)}}button{border:0;border-radius:13px;padding:12px 17px;font-weight:750;cursor:pointer;background:#8b6cff;color:#fff}.ghost{background:#1a2030}.files{display:grid;gap:10px}.file{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:15px;background:#181d2a;border-radius:16px}.actions{display:flex;gap:8px}.note{font-size:13px;color:#9ba5ba}.bar{height:8px;background:#252c3b;border-radius:20px;overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg,#8b6cff,#36d6ff);width:0}input{display:none}@media(max-width:760px){.hero{grid-template-columns:1fr}.top{align-items:flex-start;gap:12px;flex-direction:column}}
</style></head><body><main>
<div class="top"><div><div class="brand">⚡ TRANSFER</div><div class="muted">Move. Share. Done. · Made by Roshan</div></div><div class="pill">● Local only</div></div>
<div class="card"><div class="hero"><div class="drop" id="drop"><div class="mascot">⚡</div><h2>Send to phone</h2><p class="muted">Original files · No compression</p><label><button>Choose files</button><input id="pick" type="file" multiple></label><div class="note">or drag & drop here</div></div><div><h2>Phone storage</h2><p class="muted">Files sent to this device appear below.</p><div id="progress"></div><button class="ghost" onclick="load()">Refresh files</button></div></div></div>
<div class="card"><div class="top"><h2 style="margin:0">Files on phone</h2><span id="count" class="muted"></span></div><div id="list" class="files">Loading…</div></div>
</main><script>
const pick=document.getElementById('pick'),drop=document.getElementById('drop');pick.onchange=()=>send([...pick.files]);['dragenter','dragover'].forEach(e=>drop.addEventListener(e,x=>{x.preventDefault();drop.style.borderColor='#8b6cff'}));['dragleave','drop'].forEach(e=>drop.addEventListener(e,x=>{x.preventDefault();drop.style.borderColor='#3b455d'}));drop.addEventListener('drop',e=>send([...e.dataTransfer.files]));
function fmt(n){let u=['B','KB','MB','GB','TB'],i=0;while(n>=1024&&i<u.length-1){n/=1024;i++}return n.toFixed(i?1:0)+' '+u[i]}
async function send(files){for(const f of files){let p=document.getElementById('progress');p.innerHTML='<p>'+f.name+' · '+fmt(f.size)+'</p><div class="bar"><i id="bar"></i></div><p class="note">Uploading original file…</p>';await new Promise((resolve,reject)=>{let x=new XMLHttpRequest();x.open('POST','/upload');x.setRequestHeader('x-file-name',f.name);x.upload.onprogress=e=>{if(e.lengthComputable)document.getElementById('bar').style.width=(e.loaded/e.total*100)+'%'};x.onload=()=>x.status<300?resolve():reject();x.onerror=reject;x.send(f)}).catch(()=>alert('Upload failed: '+f.name));}pick.value='';load()}
async function load(){let a=await fetch('/api/files').then(r=>r.json());document.getElementById('count').textContent=a.length+' file'+(a.length==1?'':'s');document.getElementById('list').innerHTML=a.map(x=>'<div class="file"><div>📄 <b>'+esc(x.name)+'</b><div class="note">'+fmt(x.size)+'</div></div><div class="actions"><a href="/download?name='+encodeURIComponent(x.name)+'"><button>Download</button></a><button class="ghost" onclick="del(''+encodeURIComponent(x.name)+'')">Delete</button></div></div>').join('')||'<div class="muted">No files yet.</div>'}
async function del(n){if(confirm('Delete this file from phone?')){await fetch('/delete?name='+n,{method:'POST'});load()}}function esc(s){return s.replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}load();
</script></body></html>
""".trimIndent()
}

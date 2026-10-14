package com.pulsestream.app.ui.tv

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** First site-local IPv4 address (e.g. 192.168.1.x) — no permission needed. */
fun getLanIpAddress(): String? {
    return try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .firstOrNull()
    } catch (_: Exception) {
        null
    }
}

/** Renders text as a QR bitmap. */
fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}

/**
 * Tiny LAN-only HTTP server that lets a phone push addon manifest URLs to the
 * TV. Bound to the TV's LAN IP (never 0.0.0.0), guarded by a random 6-digit
 * pairing code, and stopped the moment the setup screen closes.
 */
class TvPairServer(
    private val lanIp: String,
    private val repo: StreamRepository,
    private val onEvent: (String) -> Unit
) : NanoHTTPD(lanIp, 0) {

    val code: String = (100000..999999).random().toString()

    fun pairUrl(): String = "http://$lanIp:$listeningPort/pair?code=$code"

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/pair" && session.method == Method.GET -> {
                    if (session.parameters["code"]?.firstOrNull() != code) {
                        newFixedLengthResponse(
                            Response.Status.FORBIDDEN, "text/html",
                            errorPage("Wrong pairing code.")
                        )
                    } else {
                        newFixedLengthResponse(Response.Status.OK, "text/html", formPage())
                    }
                }
                session.uri == "/add" && session.method == Method.POST ->
                    handleAdd(session)
                else ->
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", errorPage("Not found."))
            }
        } catch (_: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/html", errorPage("Something went wrong."))
        }
    }

    private fun handleAdd(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val params = session.parameters
        if (params["code"]?.firstOrNull() != code) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", errorPage("Wrong pairing code."))
        }
        val urls = (params["urls"]?.firstOrNull() ?: "")
            .split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, "text/html", formPage("Paste at least one manifest URL."))
        }
        val added = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (url in urls) {
            // addAddonFromUrl fetches + validates the manifest; serve() runs on a
            // background thread so runBlocking is safe here.
            val result = runBlocking(Dispatchers.IO) { repo.addAddonFromUrl(url) }
            result.onSuccess {
                added.add(it.name.ifBlank { url })
                onEvent("Added: ${it.name.ifBlank { url }}")
            }.onFailure {
                failed.add("$url — ${it.message ?: "couldn't add"}")
                onEvent("Failed: $url")
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html", resultPage(added, failed))
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(title: String, body: String): String = """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${esc(title)}</title>
        <style>
          body{background:#0b0b12;color:#f2f2f7;font-family:system-ui,sans-serif;
               margin:0;padding:24px;max-width:640px}
          h1{font-size:22px} p{color:#a7a7b8;line-height:1.5}
          textarea{width:100%;min-height:120px;background:#17171f;color:#f2f2f7;
                   border:1px solid #33333f;border-radius:12px;padding:12px;font-size:16px}
          button{background:#22d3ee;border:0;border-radius:999px;padding:14px 28px;
                 font-size:17px;font-weight:700;color:#0a0a12;margin-top:16px;width:100%}
          .err{color:#ff6b6b} .ok{color:#4ade80}
          li{margin:6px 0;line-height:1.4}
        </style></head><body>$body</body></html>
    """.trimIndent()

    private fun formPage(notice: String? = null): String = page(
        "Add to Plavio TV",
        """
        <h1>Add addons to Plavio TV</h1>
        <p>Paste one or more addon manifest URLs below (one per line — they
        usually end with <b>/manifest.json</b>), then tap <b>Send to TV</b>.
        The TV will fetch and validate each one.</p>
        ${if (notice != null) "<p class=\"err\">${esc(notice)}</p>" else ""}
        <form method="post" action="/add">
          <input type="hidden" name="code" value="$code">
          <textarea name="urls" placeholder="https://…/manifest.json"></textarea>
          <button type="submit">Send to TV</button>
        </form>
        """.trimIndent()
    )

    private fun resultPage(added: List<String>, failed: List<String>): String = page(
        "Done",
        """
        <h1>${if (failed.isEmpty()) "All done ✓" else "Finished with errors"}</h1>
        ${if (added.isNotEmpty()) "<p class=\"ok\">Added:</p><ul>${added.joinToString("") { "<li class=\"ok\">${esc(it)}</li>" }}</ul>" else ""}
        ${if (failed.isNotEmpty()) "<p class=\"err\">Couldn't add:</p><ul>${failed.joinToString("") { "<li class=\"err\">${esc(it)}</li>" }}</ul>" else ""}
        <p>You can close this page — check your TV for confirmation.</p>
        <p><a href="/pair?code=$code" style="color:#22d3ee">Add more</a></p>
        """.trimIndent()
    )

    private fun errorPage(message: String): String =
        page("Error", "<h1>Oops</h1><p class=\"err\">${esc(message)}</p>")
}

/**
 * TV-only screen: shows a QR code + URL so the phone can push addon manifest
 * URLs over the home Wi-Fi. Also lists installed addons with D-pad remove.
 */
@Composable
fun TvAddonSetupScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val events = remember { MutableStateFlow<List<String>>(emptyList()) }
    val eventList by events.collectAsState()
    var serverError by remember { mutableStateOf<String?>(null) }
    var pairUrl by remember { mutableStateOf<String?>(null) }
    var pairCode by remember { mutableStateOf("") }
    val server = remember {
        val lanIp = getLanIpAddress()
        if (lanIp == null) {
            serverError = "Couldn't find this TV on your Wi-Fi. Make sure it's connected."
            null
        } else {
            TvPairServer(lanIp, repository) { msg ->
                events.update { (listOf(msg) + it).take(5) }
            }
        }
    }

    DisposableEffect(server) {
        if (server != null) {
            try {
                server.start()
                pairUrl = server.pairUrl()
                pairCode = server.code
            } catch (e: Exception) {
                serverError = "Couldn't start the setup server: ${e.message}"
            }
        }
        onDispose { runCatching { server?.stop() } }
    }

    val addons by repository.addons.collectAsState(initial = emptyList())
    var confirmRemove by remember { mutableStateOf<InstalledAddon?>(null) }

    GlowBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Add from phone",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            item(key = "qr") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pairUrl != null) {
                        val bmp = remember(pairUrl) { qrBitmap(pairUrl!!) }
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Scan to add addons from your phone",
                            modifier = Modifier
                                .size(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(TextSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                serverError ?: "Starting…",
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = LocalPalette.current.accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Scan with your phone",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. Point your phone camera at the code\n" +
                                    "2. Open the page it offers\n" +
                                    "3. Paste addon manifest URL(s) and send\n" +
                                    "4. They appear on this TV — no typing with the remote",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            lineHeight = 30.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        if (pairUrl != null) {
                            Text(
                                "Or type on your phone:",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            Text(
                                pairUrl!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalPalette.current.accent
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Pairing code: $pairCode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val lastEvent = eventList.firstOrNull()
                        Text(
                            lastEvent ?: "Waiting for your phone…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (lastEvent?.startsWith("Failed") == true)
                                Color(0xFFFF6B6B)
                            else TvFocusGreen
                        )
                    }
                }
            }

            item(key = "list-header") {
                Text(
                    "Installed addons (${addons.size}) — select one and press OK to remove it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(addons, key = { it.id }) { addon ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (addon.enabled) TextPrimary.copy(alpha = 0.06f)
                            else TextSecondary.copy(alpha = 0.08f)
                        )
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .tvFocusRing(RoundedCornerShape(14.dp))
                        .clickable { confirmRemove = addon }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (addon.version.isNotBlank()) {
                            Text(
                                "v${addon.version}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove ${addon.name}",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            item(key = "hint") {
                Text(
                    "This setup page only works on your home Wi-Fi, and it stops " +
                            "listening as soon as you leave this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    confirmRemove?.let { addon ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = LocalPalette.current.panel,
            title = { Text("Remove addon?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${addon.name}\" from this TV?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.removeAddon(addon.id) }
                    confirmRemove = null
                }) {
                    Text(
                        "Remove",
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

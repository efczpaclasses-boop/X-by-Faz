package com.faz.xbyfaz

import android.graphics.Bitmap
import android.graphics.Color
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections

/**
 * One phone sign-in at a time. Values live in memory only and are wiped when the
 * server stops. They are never written to disk or logs.
 */
object PhoneSignIn {
    @Volatile var phase: String = "form"
        private set
    @Volatile var detail: String = ""
        private set
    @Volatile var failureCode: String? = null
        private set
    @Volatile var username: String? = null
        private set
    @Volatile var password: String? = null
        private set
    @Volatile var code: String? = null
        private set

    @Volatile private var server: Gate? = null
    @Volatile var url: String? = null
        private set

    fun start(): Result<String> {
        stop()
        val ip = lanIpv4() ?: return Result.failure(IllegalStateException("no-lan"))
        val token = token()
        val gate = Gate(token)
        return try {
            gate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = gate
            phase = "form"
            detail = ""
            failureCode = null
            val address = "http://$ip:${gate.listeningPort}/?t=$token"
            url = address
            Result.success(address)
        } catch (error: Exception) {
            gate.stop()
            server = null
            url = null
            Result.failure(error)
        }
    }

    fun stop() {
        server?.stop()
        server = null
        url = null
        username = null
        password = null
        code = null
    }

    fun report(nextPhase: String, text: String) {
        if (phase == "done" || phase == "failed") return
        phase = nextPhase
        detail = text
    }

    fun markNeedCode() {
        password = null
        if (phase == "done" || phase == "failed") return
        phase = "need_code"
        detail = "Need verification code"
    }

    fun markDone() {
        wipe()
        phase = "done"
        detail = "Signed in"
    }

    fun fail(error: com.faz.xbyfaz.core.model.ErrorCode) {
        username = null
        password = null
        code = null
        failureCode = error.code
        detail = "${error.code} ${error.title}"
        phase = "failed"
    }

    fun takePassword(): String? {
        val value = password
        password = null
        return value
    }

    fun takeCode(): String? {
        val value = code
        code = null
        return value
    }

    fun consumeCode() {
        code = null
    }

    private fun wipe() {
        username = null
        password = null
        code = null
        phase = "form"
    }

    private fun token(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun lanIpv4(): String? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        var fallback: String? = null
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in Collections.list(nif.inetAddresses)) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (host.startsWith("127.")) continue
                if (nif.name.startsWith("wlan") || nif.name.startsWith("eth") || nif.name.startsWith("en")) {
                    return host
                }
                if (fallback == null) fallback = host
            }
        }
        return fallback
    }

    private class Gate(private val token: String) : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response {
            val presented = session.parameters["t"]?.firstOrNull()
            if (presented == null || presented != token) {
                return text(Response.Status.FORBIDDEN, "forbidden")
            }
            val path = session.uri.substringBefore('?')
            return when {
                session.method == Method.GET && (path == "/" || path.isEmpty()) ->
                    html(PAGE)
                session.method == Method.GET && path == "/status" ->
                    json(
                        JSONObject()
                            .put("phase", phase)
                            .put("detail", detail)
                            .put("code", failureCode ?: JSONObject.NULL)
                            .toString(),
                    )
                session.method == Method.POST && path == "/credentials" -> {
                    parse(session)
                    val user = session.parameters["username"]?.firstOrNull()?.trim().orEmpty()
                    val pass = session.parameters["password"]?.firstOrNull().orEmpty()
                    if (user.isEmpty() || pass.isEmpty()) return text(Response.Status.BAD_REQUEST, "bad")
                    username = user
                    password = pass
                    phase = "working"
                    json("""{"ok":true}""")
                }
                session.method == Method.POST && path == "/code" -> {
                    parse(session)
                    val value = session.parameters["code"]?.firstOrNull()?.trim().orEmpty()
                    if (value.isEmpty()) return text(Response.Status.BAD_REQUEST, "bad")
                    code = value
                    phase = "working"
                    json("""{"ok":true}""")
                }
                else -> text(Response.Status.NOT_FOUND, "no")
            }
        }

        private fun parse(session: IHTTPSession) {
            try {
                session.parseBody(HashMap())
            } catch (_: Exception) {
                // A bad body is rejected by the empty-field check.
            }
        }

        private fun text(status: Response.Status, body: String) =
            newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

        private fun json(body: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

        private fun html(body: String) =
            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    }
}

fun qrBitmap(contents: String, size: Int = 640): Bitmap {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        contents,
        com.google.zxing.BarcodeFormat.QR_CODE,
        size,
        size,
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

private const val PAGE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>X by Faz</title>
<style>
  body { margin:0; background:#0B0D10; color:#F5F7FA; font-family:system-ui,sans-serif; }
  main { max-width:440px; margin:0 auto; padding:28px 20px 48px; }
  h1 { font-size:28px; margin:0; }
  .by { color:#4DA3FF; font-weight:700; margin:4px 0 18px; }
  p { color:#A9B2BE; line-height:1.4; }
  label { display:block; margin:14px 0 6px; font-weight:600; }
  input { width:100%; box-sizing:border-box; height:52px; border-radius:12px; border:0; padding:0 14px; font-size:18px; background:#1C2027; color:#F5F7FA; }
  button { margin-top:22px; height:54px; width:100%; border:0; border-radius:27px; background:#F5F7FA; color:#0B0D10; font-size:18px; font-weight:700; }
  .small { color:#6E7784; font-size:13px; margin-top:22px; }
  .err { color:#FF8A8A; }
</style>
</head>
<body>
<main>
  <h1>X by Faz</h1>
  <div class="by">by Faz</div>
  <p id="lead">Type the account details for the TV. They go only to this TV, then into X's own sign-in page. They are not saved.</p>
  <form id="cred">
    <label for="user">Username, email, or phone</label>
    <input id="user" name="username" autocomplete="username" autocapitalize="none">
    <label for="pass">Password</label>
    <input id="pass" name="password" type="password" autocomplete="current-password">
    <button type="submit">Send to the TV</button>
  </form>
  <form id="code" hidden>
    <label for="codefield">Verification code</label>
    <input id="codefield" inputmode="numeric" autocomplete="one-time-code">
    <button type="submit">Send code</button>
  </form>
  <p id="msg"></p>
  <p class="small">Unofficial, not affiliated with X Corp.</p>
</main>
<script>
const token = new URLSearchParams(location.search).get('t') || '';
const cred = document.getElementById('cred');
const code = document.getElementById('code');
const msg = document.getElementById('msg');
function say(text) { msg.textContent = text; }
cred.addEventListener('submit', async (event) => {
  event.preventDefault();
  say('Sending…');
  const username = cred.username.value;
  const password = cred.password.value;
  cred.password.value = '';
  const response = await fetch('/credentials?t=' + encodeURIComponent(token), { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({username: username, password: password}) });
  if (!response.ok) { say('The TV rejected that. Check you scanned this TV.'); return; }
  say('Sent. Watch the TV.');
});
code.addEventListener('submit', async (event) => {
  event.preventDefault();
  const value = document.getElementById('codefield').value;
  document.getElementById('codefield').value = '';
  const response = await fetch('/code?t=' + encodeURIComponent(token), { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({code: value}) });
  say(response.ok ? 'Code sent. Watch the TV.' : 'The TV rejected that code.');
});
setInterval(async () => {
  try {
    const response = await fetch('/status?t=' + encodeURIComponent(token));
    if (!response.ok) return;
    const data = await response.json();
    const note = data.detail || '';
    if (data.phase === 'need_code') {
      cred.hidden = true;
      code.hidden = false;
      document.getElementById('lead').textContent = 'X is asking for a verification code. Type it here and it will be entered on the TV.';
      say(note || 'Need verification code');
    } else if (data.phase === 'done') {
      cred.hidden = true;
      code.hidden = true;
      say('Signed in');
    } else if (data.phase === 'failed') {
      cred.hidden = true;
      say((data.code ? data.code + ' — ' : '') + (note || 'Sign-in stopped. Use the remote on the TV.'));
    } else if (note) {
      say(note);
    }
  } catch (e) {}
}, 1500);
</script>
</body>
</html>
"""

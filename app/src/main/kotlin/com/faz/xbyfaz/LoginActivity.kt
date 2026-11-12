package com.faz.xbyfaz

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.faz.xbyfaz.core.auth.SessionRepository
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.model.XClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen X login. This is a normal activity, not a dialog and not a Compose
 * AndroidView, so the WebView is the window surface at the panel's real pixels.
 */
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {
    @Inject lateinit var session: SessionRepository

    private lateinit var web: WebView
    private lateinit var loading: View
    private lateinit var errorPanel: LinearLayout
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var phoneMode = false
    private var pageReady = false
    private var fillInFlight = false
    private var fillStopped = false
    private var misses = 0
    private var chooserClicks = 0
    private var identifierFocused = false
    private var usernameSent = false
    private var passwordSent = false
    private var heldUser: String? = null
    private var heldPass: String? = null
    private var hostName: String? = null
    private var finished = false

    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            pollSession()
            if (pageReady && errorPanel.visibility != View.VISIBLE) {
                if (phoneMode) drivePhoneFill() else steerToIdentifier()
            }
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        phoneMode = intent.getBooleanExtra(EXTRA_PHONE, false)
        web = findViewById(R.id.login_web)
        loading = findViewById(R.id.login_loading)
        errorPanel = findViewById(R.id.login_error)
        hint = findViewById(R.id.login_hint)
        findViewById<Button>(R.id.login_retry).setOnClickListener { loadLogin() }
        findViewById<Button>(R.id.login_back).setOnClickListener { finish() }
        configureWeb()
        web.addJavascriptInterface(Signal(), "XByFazLogin")
        loadLogin()
        handler.postDelayed(tick, 1000)
        handler.postDelayed({
            if (!hint.text.toString().startsWith("XF-")) hint.visibility = View.GONE
        }, 4_000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (phoneMode) PhoneSignIn.stop()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            if (web.canGoBack() && errorPanel.visibility != View.VISIBLE) web.goBack() else finish()
            return true
        }
        if (errorPanel.visibility == View.VISIBLE) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) activateFocused()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    web.evaluateJavascript(moveFocus("next"), null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    web.evaluateJavascript(moveFocus("prev"), null)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun activateFocused() {
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.requestFocus()
        web.evaluateJavascript(ACTIVATE) { raw ->
            if (raw?.trim('"') == "input") showImeDelayed()
        }
    }

    private fun showImeDelayed() {
        web.requestFocus()
        handler.postDelayed({
            if (isFinishing) return@postDelayed
            web.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun configureWeb() {
        val settings = web.settings
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true
        com.faz.xbyfaz.core.web.BrowserIdentity.apply(web)
        web.setInitialScale(100)
        web.setBackgroundColor(android.graphics.Color.WHITE)
        web.setLayerType(View.LAYER_TYPE_NONE, null)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val host = request.url.host
                hostName = host
                if (!XClient.hostAllowedForLogin(host)) {
                    showError(ErrorCode.AUTH_002, "X left its login page ($host).")
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                hostName = Uri.parse(url).host
                pageReady = false
                chooserClicks = 0
                identifierFocused = false
                misses = 0
                maybeLeaveLogin(url)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (XClient.isXHost(Uri.parse(url).host)) revealPage()
                maybeLeaveLogin(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                hostName = Uri.parse(url).host
                maybeLeaveLogin(url)
                if (finished) return
                view.evaluateJavascript(PREPARE, null)
                view.requestFocus()
                handler.postDelayed({ checkContent(finalCheck = false) }, 600)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                hostName = Uri.parse(url).host
                maybeLeaveLogin(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showError(ErrorCode.AUTH_001, "X's login page didn't load.")
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                showError(ErrorCode.AUTH_001, "X's sign-in page closed. Retry.")
                return true
            }
        }
    }

    private fun loadLogin() {
        errorPanel.visibility = View.GONE
        loading.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        pageReady = false
        fillStopped = false
        misses = 0
        chooserClicks = 0
        usernameSent = false
        passwordSent = false
        web.settings.userAgentString = com.faz.xbyfaz.core.web.BrowserIdentity.userAgent(this)
        web.setInitialScale(100)
        web.loadUrl(XClient.LOGIN_URL)
        handler.postDelayed({ checkContent(finalCheck = true) }, 20_000)
        web.requestFocus()
    }

    private fun revealPage() {
        if (finished || errorPanel.visibility == View.VISIBLE) return
        pageReady = true
        loading.visibility = View.GONE
        web.requestFocus()
    }

    private fun checkContent(finalCheck: Boolean) {
        if (finished || errorPanel.visibility == View.VISIBLE) return
        web.evaluateJavascript(PROBE) { raw ->
            val text = raw?.trim('"').orEmpty()
            val inputs = text.substringAfter("inputs=").substringBefore(";").toIntOrNull() ?: 0
            val length = text.substringAfter("len=").substringBefore(";").toIntOrNull() ?: 0
            if (inputs > 0 || length > 40) {
                revealPage()
            } else if (finalCheck) {
                showError(ErrorCode.AUTH_006, "X's sign-in page stayed blank.")
            }
        }
    }

    private fun pollSession() {
        maybeLeaveLogin(web.url)
    }

    private fun maybeLeaveLogin(url: String?) {
        if (finished || url.isNullOrBlank()) return
        if (!XClient.isXHost(XClient.hostOf(url))) return
        val path = XClient.pathOf(url)
        val onHome = path == "/home" || path.startsWith("/home/")
        val signingIn = path.startsWith("/i/flow") || path.startsWith("/i/jf") || path.startsWith("/login") || path.startsWith("/account") || path.contains("onboard")
        if (!onHome && !(session.hasAuthCookie() && !signingIn)) return
        finished = true
        CookieManager.getInstance().flush()
        if (phoneMode) PhoneSignIn.markDone()
        setResult(RESULT_OK)
        finish()
    }

    private fun steerToIdentifier() {
        if (!XClient.isXHost(hostName) || fillInFlight || fillStopped || identifierFocused) return
        fillInFlight = true
        web.evaluateJavascript(advance(fill = false, username = null, password = null, otp = null, allowChooser = false)) { raw ->
            fillInFlight = false
            val result = raw?.trim('"').orEmpty()
            if (result == "focused-username" || result == "focused-password") identifierFocused = true
        }
    }

    private fun drivePhoneFill() {
        if (!XClient.isXHost(hostName) || fillInFlight || fillStopped) return
        if (heldUser == null) {
            val user = PhoneSignIn.username ?: return
            val pass = PhoneSignIn.takePassword() ?: return
            heldUser = user
            heldPass = pass
        }
        val user = heldUser ?: return
        val pass = heldPass
        val otp = PhoneSignIn.code
        fillInFlight = true
        web.evaluateJavascript(advance(fill = true, username = user, password = pass, otp = otp, allowChooser = false)) { raw ->
            fillInFlight = false
            onFillResult(raw?.trim('"').orEmpty())
        }
    }

    private fun onFillResult(result: String) {
        val progressed = result == "opened-identifier" || result.startsWith("submitted") || result.startsWith("waiting-") || result == "challenge" || result == "focused-username"
        if (progressed) misses = 0
        when (result) {
            "opened-identifier" -> {
                chooserClicks += 1
                note("Opening the username field…", "entering_username")
            }
            "submitted-username" -> {
                usernameSent = true
                note("Entering username…", "entering_username")
            }
            "waiting-user" -> note("Entering username…", "entering_username")
            "submitted-password" -> {
                passwordSent = true
                heldPass = null
                note("Entering password…", "entering_password")
            }
            "waiting-password" -> note("Entering password…", "entering_password")
            "challenge" -> {
                heldPass = null
                PhoneSignIn.markNeedCode()
                showStep("Need verification code")
            }
            "submitted-code" -> {
                PhoneSignIn.consumeCode()
                note("Sending the code…", "working")
            }
            "no-chooser" -> miss(ErrorCode.AUTH_009, 4)
            "no-username" -> miss(ErrorCode.AUTH_010, 6)
            "no-password" -> miss(ErrorCode.AUTH_011, 6)
            "no-code" -> miss(ErrorCode.AUTH_012, 6)
            else -> if (result == "waiting" || result.isEmpty()) miss(stepError(), 10)
        }
    }

    private fun note(text: String, phase: String) {
        showStep(text)
        PhoneSignIn.report(phase, text)
    }

    private fun showStep(text: String) {
        hint.visibility = View.VISIBLE
        hint.text = text
    }

    private fun miss(code: ErrorCode, limit: Int) {
        misses += 1
        if (misses >= limit) failFill(code)
    }

    private fun stepError(): ErrorCode = when {
        passwordSent || PhoneSignIn.phase == "need_code" -> ErrorCode.AUTH_012
        usernameSent -> ErrorCode.AUTH_011
        chooserClicks > 0 -> ErrorCode.AUTH_010
        else -> ErrorCode.AUTH_009
    }

    private fun failFill(code: ErrorCode) {
        fillStopped = true
        heldPass = null
        heldUser = null
        PhoneSignIn.fail(code)
        showStep("${code.code}  ${code.title}. Type on this screen instead.")
    }

    private fun showError(code: ErrorCode, message: String) {
        loading.visibility = View.GONE
        hint.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
        findViewById<TextView>(R.id.login_error_code).text = code.code
        findViewById<TextView>(R.id.login_error_message).text = message
        findViewById<Button>(R.id.login_retry).requestFocus()
    }

    private fun showIme() = showImeDelayed()

    private inner class Signal {
        @JavascriptInterface
        fun onInputFocused() {
            if (!XClient.isXHost(hostName)) return
            handler.post { showImeDelayed() }
        }
    }

    companion object {
        const val EXTRA_PHONE = "phone"
    }
}

private fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\u003c")
            else -> append(ch)
        }
    }
    append('"')
}

internal fun moveFocus(direction: String) = """
(function(){
  var nodes = Array.prototype.slice.call(document.querySelectorAll('input, textarea, button, a, [role=button], [role=link]'));
  nodes = nodes.filter(function(el){
    if (el.type === 'hidden' || el.disabled) return false;
    var label = ((el.innerText || el.getAttribute('aria-label') || '') + '');
    if (/google|apple/i.test(label)) return false;
    var r = el.getBoundingClientRect();
    return r.width > 8 && r.height > 8 && r.bottom > 0 && r.top < window.innerHeight;
  });
  if (!nodes.length) return 'none';
  var i = nodes.indexOf(document.activeElement);
  var next = '$direction' === 'next' ? (i < 0 ? 0 : Math.min(nodes.length - 1, i + 1)) : (i < 0 ? 0 : Math.max(0, i - 1));
  var el = nodes[next];
  el.focus();
  try { el.scrollIntoView({block:'center'}); } catch (e) {}
  if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') { try { XByFazLogin.onInputFocused(); } catch (e) {} return 'input'; }
  return 'focus';
})()
""".trimIndent()

private fun advance(fill: Boolean, username: String?, password: String?, otp: String?, allowChooser: Boolean): String {
    val user = jsString(username.orEmpty())
    val pass = jsString(password.orEmpty())
    val code = jsString(otp.orEmpty())
    val doFill = if (fill) "true" else "false"
    val allow = if (allowChooser) "true" else "false"
    return """
(function(){
  try { document.documentElement.style.zoom = '1.8'; } catch (e) {}
  var FILL = $doFill;
  var USER = $user;
  var PASS = $pass;
  var CODE = $code;
  var ALLOW = $allow;
  function docs(){
    var list = [document];
    var frames = document.querySelectorAll('iframe');
    for (var i = 0; i < frames.length; i++) {
      try { if (frames[i].contentDocument) list.push(frames[i].contentDocument); } catch (e) {}
    }
    return list;
  }
  function visible(el){
    if (!el) return false;
    var r = el.getBoundingClientRect();
    return r.width > 8 && r.height > 8;
  }
  function labelOf(el){
    return ((el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('data-testid') || '') + '').replace(/\s+/g, ' ').trim();
  }
  function enabled(el){
    return el && !el.disabled && el.getAttribute('aria-disabled') !== 'true';
  }
  function buttons(){
    var found = [];
    docs().forEach(function(doc){
      Array.prototype.forEach.call(doc.querySelectorAll('button, [role=button], a'), function(el){
        if (visible(el)) found.push(el);
      });
    });
    return found;
  }
  function inputs(){
    var found = [];
    docs().forEach(function(doc){
      Array.prototype.forEach.call(doc.querySelectorAll('input, textarea'), function(el){
        if (!visible(el) || el.type === 'hidden' || el.type === 'checkbox' || el.type === 'radio' || el.type === 'submit') return;
        found.push(el);
      });
    });
    return found;
  }
  function setVal(el, value){
    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
  }
  function clickNext(){
    var nodes = buttons().filter(enabled);
    var exact = nodes.find(function(el){ return /^(next|log in|login|sign in)$/i.test(labelOf(el)); });
    var cont = nodes.find(function(el){ return /^continue$/i.test(labelOf(el)); });
    var btn = exact || cont;
    if (!btn) return false;
    btn.click();
    return true;
  }
  function skipSocial(){
    buttons().forEach(function(el){
      if (/google|apple/i.test(labelOf(el))) {
        try { el.tabIndex = -1; } catch (e) {}
        el.setAttribute('data-xbyfaz-skip', '1');
      }
    });
  }
  function identifierButton(){
    var nodes = buttons().filter(function(el){ return enabled(el) && !/google|apple/i.test(labelOf(el)); });
    var email = nodes.find(function(el){ return /email|e-mail|username/i.test(labelOf(el)); });
    if (email) return email;
    var phone = nodes.find(function(el){ return /phone/i.test(labelOf(el)); });
    if (phone) return phone;
    return nodes.find(function(el){ return /^(sign in|log in)$/i.test(labelOf(el)); }) || null;
  }
  skipSocial();
  var text = '';
  docs().forEach(function(doc){ text += ' ' + ((doc.body && doc.body.innerText) || ''); });
  text = text.slice(0, 1600).toLowerCase();
  var fields = inputs();
  var passField = fields.find(function(el){ return (el.type || '') === 'password'; });
  function isUser(el){
    if (!el || el === passField || el.type === 'password') return false;
    var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.getAttribute('autocomplete') || '') + ' ' + (el.type || '')).toLowerCase();
    return hint.indexOf('username') >= 0 || el.name === 'text' || hint.indexOf('email') >= 0 || el.type === 'email' || el.type === 'text' || el.type === 'tel';
  }
  var userField = null;
  docs().forEach(function(doc){
    if (userField) return;
    userField = doc.querySelector('input[autocomplete="username"], input[name="text"], input[autocomplete="email"], input[type="email"]');
  });
  if (userField && !visible(userField)) userField = null;
  if (!userField) {
    userField = fields.find(function(el){
      var hint = ((el.name || '') + ' ' + (el.getAttribute('autocomplete') || '')).toLowerCase();
      return isUser(el) && (hint.indexOf('username') >= 0 || el.name === 'text' || hint.indexOf('email') >= 0);
    }) || fields.find(isUser);
  }
  var codeField = fields.find(function(el){
    var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.getAttribute('autocomplete') || '') + ' ' + (el.getAttribute('inputmode') || '')).toLowerCase();
    return hint.indexOf('one-time-code') >= 0 || hint.indexOf('verification') >= 0 || (hint.indexOf('numeric') >= 0 && /code/.test(text));
  });
  var challenge = text.indexOf('verification code') >= 0 || text.indexOf('confirmation code') >= 0 || text.indexOf('enter the code') >= 0 || text.indexOf('unusual activity') >= 0 || !!codeField;
  if (challenge && !passField) {
    if (!FILL) return 'challenge';
    if (!CODE) return 'challenge';
    var target = codeField || userField;
    if (!target) return 'no-code';
    setVal(target, CODE);
    clickNext();
    return 'submitted-code';
  }
  if (passField) {
    if (!FILL) { try { passField.focus(); } catch (e) {} return 'focused-password'; }
    if (userField && USER && (userField.value || '') !== USER) setVal(userField, USER);
    if (!PASS) return 'no-password';
    if ((passField.value || '') === PASS) return 'waiting-password';
    setVal(passField, PASS);
    clickNext();
    return 'submitted-password';
  }
    if (userField) {
    if (FILL && USER.indexOf('@') >= 0 && (userField.type === 'tel')) {
      var emailInstead = buttons().find(function(el){ return /email instead|use email|with email/i.test(labelOf(el)); });
      if (emailInstead && ALLOW) { emailInstead.click(); return 'opened-identifier'; }
    }
    if (!FILL) {
      try { userField.focus(); } catch (e) {}
      return 'focused-username';
    }
    if (!USER) return 'no-username';
    if ((userField.value || '') !== USER) setVal(userField, USER);
    if (clickNext()) return 'submitted-username';
    return 'waiting-user';
  }
  var choice = identifierButton();
  if (choice && ALLOW) { choice.click(); return 'opened-identifier'; }
  if (/see what's happening|continue with google|continue with apple|continue with phone/.test(text)) return 'no-chooser';
  return 'waiting';
})()
""".trimIndent()
}

private const val ACTIVATE = """
(function(){
  function asField(el){
    var node = el;
    while (node && node !== document.body) {
      var tag = node.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return node;
      if (node.isContentEditable) return node;
      if (node.getAttribute && node.getAttribute('role') === 'textbox') {
        var inner = node.querySelector && node.querySelector('input, textarea');
        return inner || node;
      }
      node = node.parentElement;
    }
    return null;
  }
  var field = asField(document.activeElement);
  if (!field) {
    field = document.querySelector('input[autocomplete="username"], input[name="text"], input[type="email"], input[type="text"], textarea');
  }
  if (field) {
    try { field.focus({preventScroll:true}); } catch (e) { try { field.focus(); } catch (e2) {} }
    return 'input';
  }
  var el = document.activeElement;
  if (el && el !== document.body && el !== document.documentElement) {
    var label = ((el.innerText || el.getAttribute('aria-label') || '') + '');
    if (!/google|apple/i.test(label)) el.click();
    return 'click';
  }
  return 'none';
})()
"""

private const val PREPARE = """
(function(){
  var meta = document.querySelector('meta[name=viewport]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.setAttribute('name', 'viewport');
    (document.head || document.documentElement).appendChild(meta);
  }
  meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1');
  document.documentElement.style.zoom = '1.8';
  try {
    var scroller = document.scrollingElement || document.documentElement;
    var extra = scroller.scrollWidth - scroller.clientWidth;
    if (extra > 0) scroller.scrollLeft = extra / 2;
  } catch (e) {}
  return 'ok';
})()
"""

private const val PROBE = """
(function(){
  var text = document.body ? (document.body.innerText || '') : '';
  var inputs = document.querySelectorAll('input').length;
  var title = document.title || '';
  return 'len=' + text.trim().length + ';inputs=' + inputs + ';title=' + title.slice(0, 80);
})()
"""

package com.faz.xbyfaz

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.Eyebrow
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.Wordmark
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginWelcome(
    onSignIn: () -> Unit,
    onPhone: () -> Unit,
    error: AppFailure?,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    var help by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(XColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            Wordmark()
            Spacer(Modifier.height(16.dp))
            Eyebrow("Welcome")
            Spacer(Modifier.height(6.dp))
            Text(
                "Your X videos, made for the big screen.",
                color = XColors.Text,
                fontFamily = InterFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in once. The session stays on this TV. Then browse your own feed, bookmarks, and likes.",
                color = XColors.Text2,
                fontFamily = InterFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                XButton("Sign in with X", onSignIn, primary = true, modifier = Modifier.focusRequester(focus))
                XButton("Sign in with your phone", onPhone)
                XButton("How it works", { help = true }, icon = Icons.Filled.Info)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorPanel(it, onRetry, onDismissError)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.Shield, contentDescription = null, tint = XColors.Accent)
                Text(
                    "Sign in with X opens X's own page. Arrows move between fields and OK opens the keyboard. Sign in with your phone types into that same page. Nothing is saved.",
                    color = XColors.Text2,
                    fontFamily = InterFamily,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Unofficial, not affiliated with X Corp.",
                color = XColors.Text3,
                fontFamily = InterFamily,
                fontSize = 13.sp,
            )
        }
        if (help) {
            Box(Modifier.fillMaxSize().background(XColors.Bg).padding(96.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("How sign-in works", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                    Text("1. X's real login page opens full screen, at this TV's own resolution.", color = XColors.Text, fontFamily = InterFamily, fontSize = 22.sp)
                    Text("2. Or scan the QR code and type on your phone. Those details stay in memory on the TV only long enough to be entered into X's form.", color = XColors.Text, fontFamily = InterFamily, fontSize = 22.sp)
                    Text("3. The website session stays on this TV. The password is not stored.", color = XColors.Text, fontFamily = InterFamily, fontSize = 22.sp)
                    Text("Unofficial, not affiliated with X Corp.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 18.sp)
                    XButton("Close", { help = false }, primary = true)
                }
            }
        }
    }
}

@Composable
fun PhoneQrScreen(onCancel: () -> Unit, onContinueOnTv: () -> Unit) {
    val handed = remember { booleanArrayOf(false) }
    var link by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        PhoneSignIn.start().fold(
            onSuccess = {
                link = it
                bitmap = qrBitmap(it)
            },
            onFailure = { failed = true },
        )
    }
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        while (true) {
            if (PhoneSignIn.phase == "working" && !PhoneSignIn.username.isNullOrBlank()) {
                handed[0] = true
                onContinueOnTv()
                return@LaunchedEffect
            }
            delay(400)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (!handed[0]) PhoneSignIn.stop() }
    }
    Column(
        Modifier.fillMaxSize().background(XColors.Bg).padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Wordmark()
        Text("Sign in with your phone", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        when {
            failed -> ErrorPanel(
                AppFailure(ErrorCode.AUTH_008, ErrorCode.AUTH_008.ownerHint, null),
                onRetry = onCancel,
                onSkip = onCancel,
            )
            bitmap != null && link != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "QR code for phone sign-in",
                        modifier = Modifier.width(240.dp).height(240.dp).background(XColors.White, RoundedCornerShape(12.dp)).padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("On your phone, open:", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                        Text(link!!, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The phone page asks for the account, then a verification code if X asks for one. Nothing is saved on the TV or the phone.",
                            color = XColors.Text2,
                            fontFamily = InterFamily,
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
            else -> Text("Starting…", color = XColors.Text2, fontFamily = InterFamily, fontSize = 22.sp)
        }
        XButton("Back", onCancel, primary = true)
        Text("Unofficial, not affiliated with X Corp.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp)
    }
}

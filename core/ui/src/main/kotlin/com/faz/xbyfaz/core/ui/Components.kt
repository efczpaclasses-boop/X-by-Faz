package com.faz.xbyfaz.core.ui

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.formatClock
import com.faz.xbyfaz.core.model.formatCount
import com.faz.xbyfaz.core.model.formatWhen

private val avatarBrushes = listOf(
    listOf(Color(0xFF4DA3FF), Color(0xFF7B5CFF)),
    listOf(Color(0xFF2ED3A6), Color(0xFF1E88E5)),
    listOf(Color(0xFFF7C948), Color(0xFFF0803C)),
    listOf(Color(0xFFB06CFF), Color(0xFFFF5CA8)),
    listOf(Color(0xFF5EE7DF), Color(0xFFB490CA)),
    listOf(Color(0xFF9BE15D), Color(0xFF00B09B)),
    listOf(Color(0xFFFF8A5B), Color(0xFFFF4D8D)),
)

fun imageModel(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    if (!url.startsWith("https://")) return null
    return url
}

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(XColors.Bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(XColors.Accent.copy(alpha = 0.10f), Color.Transparent),
                    radius = 900f,
                ),
            ),
    ) { content() }
}

@Composable
fun Wordmark(large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile(if (large) 48.dp else 32.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "X",
            color = XColors.Text,
            fontFamily = InterFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (large) 28.sp else 20.sp,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = " by Faz",
            color = XColors.Accent,
            fontFamily = InterFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (large) 28.sp else 20.sp,
            letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
fun LogoTile(size: Dp) {
    Box(
        Modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(size * 0.27f), ambientColor = XColors.Accent, spotColor = XColors.Accent)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFCFD6DF)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = XColors.Black,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
fun Avatar(name: String, handle: String, url: String?, size: Dp) {
    val model = imageModel(url)
    val initials = name.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { handle.take(2).uppercase() }
    val brush = avatarBrushes[(handle.hashCode() and 0x7fffffff) % avatarBrushes.size]
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(brush))
            .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials, color = Color.White, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
    }
}

@Composable
fun XButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "btn")
    val background = if (focused || primary) XColors.White else Color.White.copy(alpha = 0.12f)
    val foreground = if (focused || primary) XColors.Black else XColors.Text
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 18.dp else 0.dp, CircleShape, ambientColor = XColors.Accent, spotColor = XColors.Accent)
            .clip(CircleShape)
            .background(background)
            .border(if (focused) 3.dp else 0.dp, XColors.White, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = foreground)
        Text(
            label,
            color = foreground,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun XChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when {
        focused -> XColors.White
        selected -> XColors.AccentTint
        else -> Color.White.copy(alpha = 0.07f)
    }
    val fg = when {
        focused -> XColors.Black
        selected -> XColors.Accent2
        else -> XColors.Text2
    }
    Row(
        Modifier
            .graphicsLayer { if (focused) { scaleX = 1.06f; scaleY = 1.06f } }
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected && !focused) XColors.Accent.copy(alpha = 0.45f) else XColors.Hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, color = fg, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun VideoCard(post: Post, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 240.dp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    Column(
        modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(width * 9f / 16f)
                .shadow(if (focused) 30.dp else 10.dp, RoundedCornerShape(18.dp), ambientColor = XColors.Accent, spotColor = XColors.Accent)
                .clip(RoundedCornerShape(18.dp))
                .background(XColors.Surface2)
                .border(if (focused) 4.dp else 0.dp, XColors.White, RoundedCornerShape(18.dp)),
        ) {
            val thumb = imageModel(post.video?.posterUrl ?: post.photos.firstOrNull())
            if (thumb != null) {
                AsyncImage(thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            post.video?.durationMs?.takeIf { it > 0 }?.let {
                Text(
                    formatClock(it),
                    color = XColors.Text,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(XColors.Accent))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(post.author.name, post.author.handle, post.author.avatarUrl, 28.dp)
            Column {
                Text(
                    "${post.author.name}  @${post.author.handle}" + post.createdAt?.let { " · ${formatWhen(it)}" }.orEmpty(),
                    color = XColors.Text2,
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(post.text, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                StatsRow(post)
            }
        }
    }
}

@Composable
fun StatsRow(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Stat(Icons.Filled.Repeat, formatCount(post.repostCount))
        Stat(Icons.Filled.Favorite, formatCount(post.likeCount))
        post.viewCount?.let { Stat(Icons.Filled.Visibility, formatCount(it)) }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = XColors.Text3, modifier = Modifier.size(16.dp))
        Text(value, color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp)
    }
}

@Composable
fun FeedPostCard(post: Post, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { if (focused) { scaleX = 1.02f; scaleY = 1.02f } }
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) XColors.Surface2 else XColors.Surface1)
            .border(if (focused) 3.dp else 1.dp, if (focused) XColors.White else XColors.Hairline, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(post.author.name, post.author.handle, post.author.avatarUrl, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(post.author.name, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("@${post.author.handle}" + post.createdAt?.let { " · ${formatWhen(it)}" }.orEmpty(), color = XColors.Text2, fontFamily = InterFamily, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(post.text, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val photo = imageModel(post.photos.firstOrNull() ?: post.video?.posterUrl)
            if (photo != null) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatsRow(post)
        }
    }
}

@Composable
fun ErrorPanel(failure: AppFailure, onRetry: () -> Unit, onSkip: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(XColors.Surface1)
            .border(1.dp, XColors.Hairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text(failure.error.code, color = XColors.Accent, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(failure.userMessage, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(failure.error.ownerHint, color = XColors.Text2, fontFamily = InterFamily, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            XButton("Retry", onRetry, primary = true)
            if (onSkip != null) XButton("Skip", onSkip)
        }
    }
}

@Composable
fun LoadingState(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
        CircularProgressIndicator(color = XColors.Accent)
        Spacer(Modifier.height(18.dp))
        Text(message, color = XColors.Text2, fontFamily = InterFamily, fontSize = 15.sp)
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(Modifier.padding(vertical = 28.dp)) {
        Text(title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = XColors.Text2, fontFamily = InterFamily, fontSize = 15.sp)
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), color = XColors.Accent, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.4.sp)
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.4).sp)
        if (subtitle != null) {
            Text(subtitle, color = XColors.Text2, fontFamily = InterFamily, fontSize = 15.sp)
        }
    }
}

@Composable
fun SystemTextField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = remember { mutableStateOf(onValue) }
    val submit = remember { mutableStateOf(onSubmit) }
    latest.value = onValue
    submit.value = onSubmit
    AndroidView(
        modifier = modifier.fillMaxWidth().height(44.dp),
        factory = { ctx ->
            EditText(ctx).apply {
                this.hint = hint
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setTextColor(0xFFF5F7FA.toInt())
                setHintTextColor(0xFF6E7784.toInt())
                textSize = 16f
                isFocusable = true
                isFocusableInTouchMode = true
                background = GradientDrawable().apply {
                    setColor(0xFF1C2027.toInt())
                    cornerRadius = 10f * resources.displayMetrics.density
                }
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        latest.value(s?.toString().orEmpty())
                    }
                })
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO) {
                        submit.value()
                        true
                    } else {
                        false
                    }
                }
                setOnKeyListener { view, key, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
                        view.requestFocus()
                        view.postDelayed({
                            view.context.getSystemService(InputMethodManager::class.java)
                                ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }, 120)
                        true
                    } else {
                        false
                    }
                }
            }
        },
        update = { edit ->
            if (edit.text.toString() != value) {
                edit.setText(value)
                edit.setSelection(edit.text.length)
            }
        },
    )
}

@Composable
fun TvKeyboard(onCharacter: (String) -> Unit, onBackspace: () -> Unit, onSpace: () -> Unit) {
    var symbols by remember { mutableStateOf(false) }
    val rows = if (symbols) {
        listOf(listOf("@", "_", ".", "#", "-", "/"), listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
    } else {
        listOf(
            ('a'..'f').map { it.toString() },
            ('g'..'l').map { it.toString() },
            ('m'..'r').map { it.toString() },
            ('s'..'x').map { it.toString() },
            listOf("y", "z"),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key -> KeyCap(key) { onCharacter(key) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyCap("space", wide = true, onClick = onSpace)
            KeyCap("delete", wide = true, onClick = onBackspace)
            KeyCap(if (symbols) "abc" else "@ _ .", wide = true) { symbols = !symbols }
        }
    }
}

@Composable
private fun KeyCap(label: String, wide: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .height(64.dp)
            .width(if (wide) 150.dp else 72.dp)
            .graphicsLayer { if (focused) { scaleX = 1.08f; scaleY = 1.08f } }
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) XColors.White else XColors.Surface2)
            .border(if (focused) 3.dp else 0.dp, XColors.White, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) XColors.Black else XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = if (wide) 18.sp else 24.sp)
    }
}

@Composable
fun Kbd(label: String) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

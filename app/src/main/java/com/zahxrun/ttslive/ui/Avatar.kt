package com.zahxrun.ttslive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Avatar pemain = emoji, atau URL foto profil TikTok (diawali "http") kalau fitur foto profil aktif. */
fun isPhotoAvatar(avatar: String?): Boolean = avatar != null && avatar.startsWith("http")

@Composable
fun AvatarBadge(avatar: String?, size: Dp) {
    if (avatar.isNullOrEmpty()) return
    if (isPhotoAvatar(avatar)) {
        AsyncImage(
            model = avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(Line),
        )
    } else {
        Text(avatar, fontSize = (size.value * 0.75f).sp)
    }
}

/**
 * Avatar + nama pemain dalam satu baris. [prefix] untuk medali, [suffix] untuk poin/skor.
 * Pengganti teks "emoji nama" supaya foto profil ikut tampil.
 */
@Composable
fun AvatarLabel(
    avatar: String?,
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    prefix: String = "",
    suffix: String = "",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) {
            Text("$prefix ", color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1)
        }
        if (!avatar.isNullOrEmpty()) {
            AvatarBadge(avatar, (fontSize.value * 1.5f).dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text + suffix,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

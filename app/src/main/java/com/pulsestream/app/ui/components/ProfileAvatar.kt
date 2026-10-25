package com.pulsestream.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsestream.app.R
import com.pulsestream.app.ui.theme.LocalPalette
import java.io.File

/** One built-in preset avatar (owner-approved set, 2026-10-24). */
data class PresetAvatar(val id: String, val label: String, @DrawableRes val resId: Int)

/** The 12 presets: 4 men, 4 women, 4 kids — one flat style, Plavio colors. */
val PRESET_AVATARS: List<PresetAvatar> = listOf(
    PresetAvatar("man_beard", "Man with beard", R.drawable.avatar_man_beard),
    PresetAvatar("man_glasses", "Man with glasses", R.drawable.avatar_man_glasses),
    PresetAvatar("man_cap", "Man with cap", R.drawable.avatar_man_cap),
    PresetAvatar("man_blond", "Blond man", R.drawable.avatar_man_blond),
    PresetAvatar("woman_hijab", "Woman with hijab", R.drawable.avatar_woman_hijab),
    PresetAvatar("woman_longhair", "Woman with long hair", R.drawable.avatar_woman_longhair),
    PresetAvatar("woman_bun", "Woman with bun", R.drawable.avatar_woman_bun),
    PresetAvatar("woman_curly", "Woman with curly hair", R.drawable.avatar_woman_curly),
    PresetAvatar("kid_boy", "Boy", R.drawable.avatar_kid_boy),
    PresetAvatar("kid_pigtails", "Girl with pigtails", R.drawable.avatar_kid_pigtails),
    PresetAvatar("kid_cap", "Kid with cap", R.drawable.avatar_kid_cap),
    PresetAvatar("kid_curly", "Kid with curly hair", R.drawable.avatar_kid_curly)
)

fun presetAvatar(id: String): PresetAvatar? = PRESET_AVATARS.firstOrNull { it.id == id }

/** The preset named by a stored profileAvatar value ("preset:<id>"), if any. */
fun profileAvatarPreset(value: String): PresetAvatar? =
    if (value.startsWith("preset:")) presetAvatar(value.removePrefix("preset:")) else null

/**
 * The user's account picture as a filled circle: their own photo when
 * [avatarSetting] is "photo", a preset when it is "preset:<id>", else a
 * placeholder — [placeholderLetter] when given, a person glyph otherwise.
 * [avatarSetting] is SettingsStore.profileAvatar; [photoFile] is
 * SettingsStore.profilePhotoFile().
 */
@Composable
fun ProfileAvatarImage(
    avatarSetting: String,
    photoFile: File?,
    modifier: Modifier = Modifier,
    placeholderLetter: String? = null
) {
    val palette = LocalPalette.current
    val preset = profileAvatarPreset(avatarSetting)
    val photo = photoFile?.takeIf { avatarSetting == "photo" && it.exists() }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            photo != null -> {
                // Cache key carries the mtime so a re-picked photo
                // replaces the old one instead of serving the cache.
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo)
                        .diskCacheKey("profile_photo_${photo.lastModified()}")
                        .memoryCacheKey("profile_photo_${photo.lastModified()}")
                        .crossfade(false)
                        .build(),
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            preset != null -> {
                Image(
                    painter = painterResource(preset.resId),
                    contentDescription = preset.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            placeholderLetter != null -> {
                Text(
                    placeholderLetter,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }
            else -> {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.fillMaxSize(0.62f)
                )
            }
        }
    }
}

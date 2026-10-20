package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

private data class OssLibrary(
    val name: String,
    val license: String,
    val url: String
)

/** Third-party open-source libraries shipped in Plavio, with their licenses. */
private val OSS_LIBRARIES = listOf(
    OssLibrary("Plavio", "MIT License", "https://github.com/kotlinsyd/Plavio"),
    OssLibrary("AndroidX Core", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    OssLibrary("AndroidX Lifecycle", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    OssLibrary("AndroidX Activity", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    OssLibrary("Jetpack Compose", "Apache License 2.0", "https://developer.android.com/compose"),
    OssLibrary("Material 3 for Compose", "Apache License 2.0", "https://m3.material.io"),
    OssLibrary("Navigation Compose", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    OssLibrary("Room", "Apache License 2.0", "https://developer.android.com/training/data-storage/room"),
    OssLibrary("Kotlin Coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OssLibrary("Kotlin Serialization", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OssLibrary("Media3 ExoPlayer", "Apache License 2.0", "https://developer.android.com/media/media3/exoplayer"),
    OssLibrary("OkHttp", "Apache License 2.0", "https://square.github.io/okhttp/"),
    OssLibrary("Coil", "Apache License 2.0", "https://coil-kt.github.io/coil/"),
    OssLibrary("Firebase Crashlytics", "Apache License 2.0", "https://firebase.google.com/docs/crashlytics"),
    OssLibrary("Firebase Analytics", "Apache License 2.0", "https://firebase.google.com/docs/analytics"),
    OssLibrary("NewPipeExtractor", "GNU GPL v3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    OssLibrary("FFmpeg (audio decoders)", "LGPL 2.1 or later", "https://ffmpeg.org"),
)

private const val APACHE_2_0_TEXT = """Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.

"License" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.

"Licensor" shall mean the copyright owner or entity authorized by the copyright owner that is granting the License.

"Legal Entity" shall mean the union of the acting entity and all other entities that control, are controlled by, or are under common control with that entity.

"You" (or "Your") shall mean an individual or Legal Entity exercising permissions granted by this License.

"Source" form shall mean the preferred form for making modifications, including but not limited to software source code, documentation source, and configuration files.

"Object" form shall mean any form resulting from mechanical transformation or translation of a Source form, including but not limited to compiled object code, generated documentation, and conversions to other media types.

"Work" shall mean the work of authorship, whether in Source or Object form, made available under the License, as indicated by a copyright notice that is included in or attached to the work.

"Derivative Works" shall mean any work, whether in Source or Object form, that is based on (or derived from) the Work and for which the editorial revisions, annotations, elaborations, or other modifications represent, as a whole, an original work of authorship.

"Contribution" shall mean any work of authorship, including the original version of the Work and any modifications or additions to that Work or Derivative Works thereof, that is intentionally submitted to Licensor for inclusion in the Work.

"Contributor" shall mean Licensor and any individual or Legal Entity on behalf of whom a Contribution has been received by Licensor and subsequently incorporated within the Work.

2. Grant of Copyright License. Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright license to reproduce, prepare Derivative Works of, publicly display, publicly perform, sublicense, and distribute the Work and such Derivative Works in Source or Object form.

3. Grant of Patent License. Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable (except as stated in this section) patent license to make, have made, Use, offer to sell, sell, import, and otherwise transfer the Work.

4. Redistribution. You may reproduce and distribute copies of the Work or Derivative Works thereof in any medium, with or without modifications, and in Source or Object form, provided that You meet the following conditions:

   (a) You must give any other recipients of the Work or Derivative Works a copy of this License; and

   (b) You must cause any modified files to carry prominent notices stating that You changed the files; and

   (c) You must retain, in the Source form of any Derivative Works that You distribute, all copyright, patent, trademark, and attribution notices from the Source form of the Work; and

   (d) If the Work includes a "NOTICE" text file as part of its distribution, then any Derivative Works that You distribute must include a readable copy of the attribution notices contained within such NOTICE file.

5. Submission of Contributions. Unless You explicitly state otherwise, any Contribution intentionally submitted for inclusion in the Work by You to the Licensor shall be under the terms and conditions of this License, without any additional terms or conditions.

6. Trademarks. This License does not grant permission to use the trade names, trademarks, service marks, or product names of the Licensor, except as required for reasonable and customary use in describing the origin of the Work and reproducing the content of the NOTICE file.

7. Disclaimer of Warranty. Unless required by applicable law or agreed to in writing, Licensor provides the Work (and each Contributor provides its Contributions) on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

8. Limitation of Liability. In no event and under no legal theory shall any Contributor be liable to You for damages arising out of the use or inability to use the Work.

9. Accepting Warranty or Additional Liability. While redistributing the Work or Derivative Works thereof, You may choose to offer, and charge a fee for, acceptance of support, warranty, indemnity, or other liability obligations.

END OF TERMS AND CONDITIONS"""

private const val LGPL_NOTE = """The project identifies the bundled FFmpeg audio decoder as LGPL 2.1-or-later and loads it as a native library (libffmpegJNI.so). The package contains ARM32 and ARM64 binaries.

The exact FFmpeg revision, source commit, patches, configure options, toolchain, and corresponding source for these binaries are not recorded in this source package. Their build/source provenance is unknown. The generic FFmpeg download page is not identified as the corresponding source for this binary. The distributor should identify and provide the matching source and build materials required by the applicable LGPL terms.

Full license text: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"""

private const val GPL_NOTE = """NewPipeExtractor is used for YouTube trailer stream extraction. The dependency declared by this project is TeamNewPipe/NewPipeExtractor v0.26.5, under the GNU General Public License v3.0.

Project source and license: https://github.com/TeamNewPipe/NewPipeExtractor
GPL v3.0 text: https://www.gnu.org/licenses/gpl-3.0.html"""

private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 Najam

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

@Composable
fun LicensesScreen(
    onBack: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Open source licenses",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            item(key = "intro") {
                Text(
                    text = "Plavio is built with these open-source projects. Tap a library to read its license.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            items(OSS_LIBRARIES, key = { it.name }) { lib ->
                val isExpanded = expanded == lib.name
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = if (isExpanded) null else lib.name
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = lib.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = lib.license,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (isExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = when {
                                    lib.license.startsWith("LGPL") -> LGPL_NOTE
                                    lib.license.startsWith("GNU GPL") -> GPL_NOTE
                                    lib.license == "MIT License" -> MIT_LICENSE_TEXT
                                    else -> APACHE_2_0_TEXT
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

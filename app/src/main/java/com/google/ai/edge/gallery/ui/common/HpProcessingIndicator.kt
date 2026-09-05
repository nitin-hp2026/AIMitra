/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import kotlin.math.roundToInt

/** Horizontally scrolling HP logo shown while the model is preparing or processing input. */
@Composable
fun HpProcessingIndicator(
  modifier: Modifier = Modifier,
  logoHeight: Dp = 40.dp,
  statusMessage: String? = null,
  submessage: String? = null,
) {
  val density = LocalDensity.current
  val logoSlotWidth = with(density) { (logoHeight * 2.4f).toPx() }

  val infiniteTransition = rememberInfiniteTransition(label = "hp-logo-scroll")
  val scrollOffset by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = -logoSlotWidth * 3f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "hp-logo-offset",
    )

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().height(logoHeight + 12.dp).clipToBounds(),
      contentAlignment = Alignment.CenterStart,
    ) {
      Row(
        modifier = Modifier.offset { IntOffset(scrollOffset.roundToInt(), 0) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(8) {
          Image(
            painter = painterResource(R.drawable.hp_logo),
            contentDescription = null,
            modifier = Modifier.height(logoHeight).padding(horizontal = 18.dp),
            contentScale = ContentScale.Fit,
          )
        }
      }
    }

    if (!statusMessage.isNullOrEmpty()) {
      Text(
        statusMessage,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
    }

    if (!submessage.isNullOrEmpty()) {
      Text(
        submessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
    }
  }
}

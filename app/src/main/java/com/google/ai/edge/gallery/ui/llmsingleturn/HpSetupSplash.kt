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

package com.google.ai.edge.gallery.ui.llmsingleturn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/** Branded splash shown while the app prepares; fades into the main UI when ready. */
@Composable
fun HpSetupSplash(
  message: String,
  progress: Float?,
  modifier: Modifier = Modifier,
  submessage: String? = null,
  onRetry: (() -> Unit)? = null,
) {
  val titleTransition = rememberInfiniteTransition(label = "splash-title")
  val titleAlpha by
    titleTransition.animateFloat(
      initialValue = 0.82f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "splash-title-alpha",
    )
  val titleScale by
    titleTransition.animateFloat(
      initialValue = 0.98f,
      targetValue = 1.02f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "splash-title-scale",
    )

  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    HpResponseProcessingIndicator(
      isProcessing = progress != null || onRetry == null,
      logoSize = 40.dp,
      ringSize = 64.dp,
      modifier = Modifier.padding(bottom = 20.dp),
    )

    Text(
      stringResource(R.string.app_name),
      style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
      color = HpRed,
      textAlign = TextAlign.Center,
      modifier =
        Modifier
          .fillMaxWidth()
          .graphicsLayer {
            alpha = titleAlpha
            scaleX = titleScale
            scaleY = titleScale
          },
    )

    AnimatedVisibility(
      visible = message.isNotEmpty(),
      enter = fadeIn(tween(500)) + slideInVertically { it / 4 },
      exit = fadeOut(tween(300)),
    ) {
      Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
      )
    }

    if (submessage != null) {
      Text(
        submessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      )
    }

    if (progress != null) {
      Text(
        "${(progress * 100).toInt()}%",
        style = MaterialTheme.typography.headlineMedium,
        color = HpBlue,
        modifier = Modifier.padding(top = 20.dp),
      )
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.padding(top = 16.dp).width(220.dp),
        color = HpBlue,
        trackColor = HpRed.copy(alpha = 0.15f),
      )
    }

    if (onRetry != null) {
      Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) { Text("Retry") }
    }
  }
}

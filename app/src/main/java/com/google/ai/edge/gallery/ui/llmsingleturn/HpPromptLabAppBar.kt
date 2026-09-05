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

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.ai.edge.gallery.R

val HpBlue = Color(0xFF0096D6)
val HpRed = Color(0xFFCE1126)

/**
 * Blue HP logo fixed in the centre with an outer ring that spins while [isProcessing].
 * Used in the response output area (top-left).
 */
@Composable
fun HpResponseProcessingIndicator(
  isProcessing: Boolean,
  modifier: Modifier = Modifier,
  logoSize: Dp = 28.dp,
  ringSize: Dp = 44.dp,
  statusMessage: String? = null,
) {
  val ringTransition = rememberInfiniteTransition(label = "hp-ring")
  val ringRotation by
    ringTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1400, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "hp-ring-rotation",
    )
  val ringAlpha by
    ringTransition.animateFloat(
      initialValue = 0.45f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "hp-ring-alpha",
    )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
      Canvas(
        modifier =
          Modifier
            .size(ringSize)
            .graphicsLayer { rotationZ = if (isProcessing) ringRotation else 0f },
      ) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val ringColor = if (isProcessing) HpBlue.copy(alpha = ringAlpha) else HpRed.copy(alpha = 0.35f)
        drawArc(
          color = ringColor,
          startAngle = 0f,
          sweepAngle = if (isProcessing) 270f else 360f,
          useCenter = false,
          style = stroke,
        )
        if (isProcessing) {
          drawArc(
            color = HpBlue.copy(alpha = 0.2f),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            style = stroke,
          )
        }
      }
      Image(
        painter = painterResource(R.drawable.hp_logo),
        contentDescription = null,
        modifier = Modifier.size(logoSize),
        colorFilter = ColorFilter.tint(HpBlue),
        contentScale = ContentScale.Fit,
      )
    }

    if (!statusMessage.isNullOrEmpty()) {
      Text(
        statusMessage,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HpPromptLabAppBar(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activity = LocalActivity.current
  var isFullscreen by remember { mutableStateOf(false) }

  CenterAlignedTopAppBar(
    modifier = modifier,
    colors =
      TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = Color.White,
        titleContentColor = HpBlue,
        actionIconContentColor = HpBlue,
        navigationIconContentColor = HpBlue,
      ),
    navigationIcon = {
      // Balance window controls so the title stays centred.
      Spacer(modifier = Modifier.width(108.dp))
    },
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Image(
          painter = painterResource(R.drawable.hp_logo),
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          colorFilter = ColorFilter.tint(HpBlue),
          contentScale = ContentScale.Fit,
        )
        Text(
          "AI Mitra",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = HpBlue,
        )
      }
    },
    actions = {
      Row(
        modifier = Modifier.fillMaxHeight().width(108.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
      ) {
        WindowControlButton(label = "−", onClick = { activity?.moveTaskToBack(true) })
        WindowControlButton(
          label = if (isFullscreen) "❐" else "□",
          onClick = {
            activity?.let { act ->
              isFullscreen = !isFullscreen
              val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
              WindowCompat.setDecorFitsSystemWindows(act.window, !isFullscreen)
              if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                  WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
              } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
              }
            }
          },
        )
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_close_icon),
            modifier = Modifier.size(18.dp),
            tint = HpBlue,
          )
        }
      }
    },
  )
}

@Composable
private fun WindowControlButton(label: String, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
    Text(label, fontSize = 14.sp, color = HpBlue)
  }
}

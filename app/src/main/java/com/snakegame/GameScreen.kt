package com.snakegame

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun GameScreen(engine: GameEngine) {
    val state by engine.uiState.collectAsState()

    // Swipe gesture vars
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragStart = it },
                    onDragEnd   = {},
                    onDrag      = { _, dragAmount ->
                        val dx = dragAmount.x
                        val dy = dragAmount.y
                        if (abs(dx) > 10f || abs(dy) > 10f) {
                            if (abs(dx) > abs(dy)) {
                                engine.changeDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                            } else {
                                engine.changeDirection(if (dy > 0) Direction.DOWN else Direction.UP)
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top HUD ──────────────────────────────────────────────────────
            TopHud(state = state, onPause = { engine.pauseResume() })

            // ── Game Board ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                GameBoard(state = state)

                // Overlay screens
                AnimatedVisibility(
                    visible = state.state == GameState.IDLE,
                    enter   = fadeIn(),
                    exit    = fadeOut()
                ) {
                    StartScreen(onStart = { engine.startGame() })
                }

                AnimatedVisibility(
                    visible = state.state == GameState.PAUSED,
                    enter   = fadeIn() + scaleIn(),
                    exit    = fadeOut() + scaleOut()
                ) {
                    PausedScreen(onResume = { engine.pauseResume() })
                }

                AnimatedVisibility(
                    visible = state.state == GameState.GAME_OVER,
                    enter   = fadeIn() + scaleIn(),
                    exit    = fadeOut()
                ) {
                    GameOverScreen(
                        score     = state.score,
                        highScore = state.highScore,
                        onRestart = { engine.startGame() }
                    )
                }
            }

            // ── D-Pad Controls ───────────────────────────────────────────────
            DPad(
                onUp    = { engine.changeDirection(Direction.UP) },
                onDown  = { engine.changeDirection(Direction.DOWN) },
                onLeft  = { engine.changeDirection(Direction.LEFT) },
                onRight = { engine.changeDirection(Direction.RIGHT) },
                modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
            )
        }
    }
}

// ── Top HUD ───────────────────────────────────────────────────────────────────

@Composable
fun TopHud(state: SnakeUiState, onPause: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Score
        Column {
            Text(
                text = "SKOR",
                fontSize = 10.sp,
                color = GameColors.ScoreText.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "${state.score}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = GameColors.ScoreText,
                fontFamily = FontFamily.Monospace
            )
        }

        // Level + Power indicator
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isPowerUp) {
                Text(
                    text = "⚡ POWER",
                    fontSize = 11.sp,
                    color = GameColors.PowerUp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "LVL ${state.level}",
                fontSize = 14.sp,
                color = GameColors.ScoreText.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // High score + Pause
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "TERBAIK",
                fontSize = 10.sp,
                color = GameColors.ScoreText.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${state.highScore}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GameColors.BonusFood,
                    fontFamily = FontFamily.Monospace
                )
                if (state.state == GameState.RUNNING || state.state == GameState.PAUSED) {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = if (state.state == GameState.PAUSED) "▶" else "⏸",
                            fontSize = 16.sp,
                            color = GameColors.ScoreText
                        )
                    }
                }
            }
        }
    }

    // Divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GameColors.SnakeHead.copy(alpha = 0.2f))
    )
}

// ── Game Board Canvas ─────────────────────────────────────────────────────────

@Composable
fun GameBoard(state: SnakeUiState) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnim.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BOARD_WIDTH.toFloat() / BOARD_HEIGHT.toFloat())
            .clip(RoundedCornerShape(8.dp))
            .background(GameColors.Background)
    ) {
        val cellW = size.width  / BOARD_WIDTH
        val cellH = size.height / BOARD_HEIGHT
        val gap   = 1.5f

        // Draw grid
        for (x in 0 until BOARD_WIDTH) {
            for (y in 0 until BOARD_HEIGHT) {
                drawRoundRect(
                    color        = GameColors.GridLine,
                    topLeft      = Offset(x * cellW + gap, y * cellH + gap),
                    size         = Size(cellW - gap * 2, cellH - gap * 2),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }

        // Draw food (pulsing red circle)
        drawFood(state.food, cellW, cellH, GameColors.Food, pulse)

        // Draw bonus food
        state.bonusFood?.let { bf ->
            drawFood(bf, cellW, cellH, GameColors.BonusFood, pulse)
        }

        // Draw snake
        state.snake.forEachIndexed { i, pt ->
            val color = when (i) {
                0    -> if (state.isPowerUp) GameColors.PowerUp else GameColors.SnakeHead
                state.snake.lastIndex -> GameColors.SnakeTail
                else -> {
                    val t = i.toFloat() / state.snake.size
                    lerp(GameColors.SnakeBody, GameColors.SnakeTail, t)
                }
            }
            val isHead = i == 0
            val inset  = if (isHead) 0.5f else gap + 0.5f
            drawRoundRect(
                color        = color,
                topLeft      = Offset(pt.x * cellW + inset, pt.y * cellH + inset),
                size         = Size(cellW - inset * 2, cellH - inset * 2),
                cornerRadius = CornerRadius(if (isHead) 4f else 3f)
            )
            // Head eyes
            if (isHead) {
                val ex = pt.x * cellW + cellW * 0.25f
                val ey = pt.y * cellH + cellH * 0.3f
                drawCircle(GameColors.Background, radius = cellW * 0.1f, center = Offset(ex, ey))
                drawCircle(GameColors.Background, radius = cellW * 0.1f, center = Offset(pt.x * cellW + cellW * 0.75f, ey))
            }
        }

        // Border glow
        drawRoundRect(
            color        = GameColors.SnakeHead.copy(alpha = 0.15f),
            topLeft      = Offset(0f, 0f),
            size         = size,
            cornerRadius = CornerRadius(8f),
            style        = Stroke(width = 2f)
        )
    }
}

fun DrawScope.drawFood(pt: Point, cellW: Float, cellH: Float, color: Color, pulse: Float) {
    val cx = pt.x * cellW + cellW / 2
    val cy = pt.y * cellH + cellH / 2
    val r  = (cellW.coerceAtMost(cellH) / 2 - 2f) * pulse
    // Glow
    drawCircle(color.copy(alpha = 0.25f), radius = r * 1.6f, center = Offset(cx, cy))
    drawCircle(color.copy(alpha = 0.5f),  radius = r * 1.2f, center = Offset(cx, cy))
    drawCircle(color,                      radius = r,         center = Offset(cx, cy))
}

fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t
)

// ── D-Pad ─────────────────────────────────────────────────────────────────────

@Composable
fun DPad(
    onUp: () -> Unit, onDown: () -> Unit,
    onLeft: () -> Unit, onRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnColor = GameColors.SnakeHead.copy(alpha = 0.15f)
    val iconColor = GameColors.ScoreText

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DPadButton("▲", iconColor, btnColor, onUp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DPadButton("◀", iconColor, btnColor, onLeft)
            // Center spacer
            Box(modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GameColors.SnakeHead.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Text("●", color = GameColors.ScoreText.copy(alpha = 0.3f), fontSize = 20.sp)
            }
            DPadButton("▶", iconColor, btnColor, onRight)
        }
        DPadButton("▼", iconColor, btnColor, onDown)
    }
}

@Composable
fun DPadButton(label: String, textColor: Color, bgColor: Color, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier.size(64.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = bgColor),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(label, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Start Screen ──────────────────────────────────────────────────────────────

@Composable
fun StartScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text       = "🐍",
                fontSize   = 72.sp
            )
            Text(
                text       = "SNAKE",
                fontSize   = 48.sp,
                fontWeight = FontWeight.Black,
                color      = GameColors.ScoreText,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            Text(
                text      = "Geser layar atau pakai D-Pad",
                fontSize  = 14.sp,
                color     = GameColors.ScoreText.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = GameColors.SnakeHead),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text(
                    text       = "MULAI",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Black,
                    color      = GameColors.Background,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoCard()
        }
    }
}

@Composable
fun InfoCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GameColors.SnakeHead.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("🔴", "Makanan biasa  +10 poin")
            InfoRow("🟡", "Bonus makanan  +30 poin")
            InfoRow("⚡", "Power-up: tembus dinding!")
        }
    }
}

@Composable
fun InfoRow(icon: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Text(text, fontSize = 12.sp, color = GameColors.ScoreText.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
    }
}

// ── Paused Screen ─────────────────────────────────────────────────────────────

@Composable
fun PausedScreen(onResume: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("⏸", fontSize = 56.sp)
            Text(
                text       = "PAUSE",
                fontSize   = 36.sp,
                fontWeight = FontWeight.Black,
                color      = GameColors.ScoreText,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp
            )
            Button(
                onClick = onResume,
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = GameColors.SnakeHead),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text(
                    text       = "▶  LANJUT",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Black,
                    color      = GameColors.Background,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── Game Over Screen ──────────────────────────────────────────────────────────

@Composable
fun GameOverScreen(score: Int, highScore: Int, onRestart: () -> Unit) {
    val isNewHigh = score == highScore && score > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0x99FF3B30),
                        GameColors.Background.copy(alpha = 0.95f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💀", fontSize = 64.sp)
            Text(
                text       = "GAME OVER",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Black,
                color      = Color(0xFFFF3B30),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            if (isNewHigh) {
                Surface(shape = RoundedCornerShape(8.dp), color = GameColors.BonusFood.copy(alpha = 0.15f)) {
                    Text(
                        text = "🏆  REKOR BARU!",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontSize = 14.sp,
                        color = GameColors.BonusFood,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Surface(shape = RoundedCornerShape(12.dp), color = GameColors.SnakeHead.copy(alpha = 0.08f)) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScoreRow("SKOR", "$score", GameColors.ScoreText)
                    ScoreRow("TERBAIK", "$highScore", GameColors.BonusFood)
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRestart,
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = GameColors.SnakeHead),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text(
                    text       = "🔄  MAIN LAGI",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Black,
                    color      = GameColors.Background,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ScoreRow(label: String, value: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = GameColors.ScoreText.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}

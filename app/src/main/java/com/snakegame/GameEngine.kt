package com.snakegame

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// ── Constants ─────────────────────────────────────────────────────────────────

const val BOARD_WIDTH  = 20
const val BOARD_HEIGHT = 30

// ── Data ──────────────────────────────────────────────────────────────────────

data class Point(val x: Int, val y: Int)

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class GameState { IDLE, RUNNING, PAUSED, GAME_OVER }

data class SnakeUiState(
    val snake: List<Point>      = listOf(Point(10, 15), Point(10, 16), Point(10, 17)),
    val food: Point             = Point(5, 5),
    val bonusFood: Point?       = null,
    val direction: Direction    = Direction.UP,
    val state: GameState        = GameState.IDLE,
    val score: Int              = 0,
    val highScore: Int          = 0,
    val level: Int              = 1,
    val foodEaten: Int          = 0,
    val isPowerUp: Boolean      = false   // ular sementara bisa tembus dinding
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GameEngine : ViewModel() {

    private val _uiState = MutableStateFlow(SnakeUiState())
    val uiState: StateFlow<SnakeUiState> = _uiState.asStateFlow()

    private var gameLoop: Job? = null
    private var pendingDirection: Direction = Direction.UP
    private var bonusFoodTimer = 0
    private var powerUpTimer   = 0

    // Kecepatan berdasarkan level (ms per tick)
    private fun tickSpeed(level: Int) = when {
        level >= 10 -> 80L
        level >= 7  -> 110L
        level >= 5  -> 140L
        level >= 3  -> 170L
        else        -> 200L
    }

    fun startGame() {
        val startSnake = listOf(
            Point(BOARD_WIDTH / 2,     BOARD_HEIGHT / 2),
            Point(BOARD_WIDTH / 2,     BOARD_HEIGHT / 2 + 1),
            Point(BOARD_WIDTH / 2,     BOARD_HEIGHT / 2 + 2)
        )
        val hs = _uiState.value.highScore
        pendingDirection = Direction.UP
        bonusFoodTimer   = 0
        powerUpTimer     = 0

        _uiState.value = SnakeUiState(
            snake     = startSnake,
            food      = randomFood(startSnake),
            highScore = hs
        )
        _uiState.value = _uiState.value.copy(state = GameState.RUNNING)
        startLoop()
    }

    fun pauseResume() {
        val s = _uiState.value
        when (s.state) {
            GameState.RUNNING -> {
                gameLoop?.cancel()
                _uiState.value = s.copy(state = GameState.PAUSED)
            }
            GameState.PAUSED  -> {
                _uiState.value = s.copy(state = GameState.RUNNING)
                startLoop()
            }
            else -> {}
        }
    }

    fun changeDirection(newDir: Direction) {
        val cur = _uiState.value.direction
        // Cegah balik arah langsung
        val invalid = (cur == Direction.UP   && newDir == Direction.DOWN)  ||
                      (cur == Direction.DOWN  && newDir == Direction.UP)    ||
                      (cur == Direction.LEFT  && newDir == Direction.RIGHT) ||
                      (cur == Direction.RIGHT && newDir == Direction.LEFT)
        if (!invalid) pendingDirection = newDir
    }

    private fun startLoop() {
        gameLoop?.cancel()
        gameLoop = viewModelScope.launch {
            while (_uiState.value.state == GameState.RUNNING) {
                delay(tickSpeed(_uiState.value.level))
                tick()
            }
        }
    }

    private fun tick() {
        val state = _uiState.value
        val dir   = pendingDirection
        val head  = state.snake.first()

        // Hitung posisi kepala baru
        var nx = head.x + when (dir) { Direction.RIGHT -> 1; Direction.LEFT -> -1; else -> 0 }
        var ny = head.y + when (dir) { Direction.DOWN  -> 1; Direction.UP   -> -1; else -> 0 }

        // Power-up: tembus dinding
        if (state.isPowerUp) {
            nx = (nx + BOARD_WIDTH)  % BOARD_WIDTH
            ny = (ny + BOARD_HEIGHT) % BOARD_HEIGHT
        } else {
            // Normal: tabrak dinding = game over
            if (nx < 0 || nx >= BOARD_WIDTH || ny < 0 || ny >= BOARD_HEIGHT) {
                endGame(); return
            }
        }

        val newHead = Point(nx, ny)

        // Tabrak badan sendiri = game over
        if (state.snake.contains(newHead)) { endGame(); return }

        val ateFood      = newHead == state.food
        val ateBonusFood = state.bonusFood != null && newHead == state.bonusFood
        val atePowerFood = ateBonusFood && bonusFoodTimer > 15  // bonus merah = power-up

        // Gerak ular
        val newSnake = buildList {
            add(newHead)
            addAll(if (ateFood || ateBonusFood) state.snake else state.snake.dropLast(1))
        }

        // Hitung score
        var addScore = 0
        var foodEaten = state.foodEaten
        if (ateFood)      { addScore += 10 * state.level; foodEaten++ }
        if (ateBonusFood) { addScore += 30 * state.level; foodEaten++ }

        val newScore = state.score + addScore
        val newHigh  = maxOf(newScore, state.highScore)

        // Level naik setiap 5 buah
        val newLevel = 1 + (foodEaten / 5)

        // Generate makanan baru
        val newFood       = if (ateFood) randomFood(newSnake) else state.food
        val newBonusFood  = when {
            ateBonusFood      -> null
            bonusFoodTimer == 0 && Random.nextInt(100) < 15 -> randomFood(newSnake, listOf(newFood))
            else              -> state.bonusFood
        }

        // Timer bonus food (hilang setelah ~20 tick)
        bonusFoodTimer = when {
            newBonusFood == null -> 0
            ateBonusFood         -> 0
            bonusFoodTimer > 0   -> bonusFoodTimer + 1
            else                 -> if (newBonusFood != state.bonusFood) 1 else 0
        }
        val removedBonusFood = if (bonusFoodTimer > 20) null else newBonusFood

        // Power-up timer
        powerUpTimer = when {
            atePowerFood     -> 10  // aktif 10 tick
            powerUpTimer > 0 -> powerUpTimer - 1
            else             -> 0
        }

        _uiState.value = state.copy(
            snake      = newSnake,
            food       = newFood,
            bonusFood  = removedBonusFood,
            direction  = dir,
            score      = newScore,
            highScore  = newHigh,
            level      = newLevel,
            foodEaten  = foodEaten,
            isPowerUp  = powerUpTimer > 0
        )
    }

    private fun endGame() {
        gameLoop?.cancel()
        val s = _uiState.value
        _uiState.value = s.copy(state = GameState.GAME_OVER)
    }

    private fun randomFood(snake: List<Point>, exclude: List<Point> = emptyList()): Point {
        val all = buildList {
            for (x in 0 until BOARD_WIDTH)
                for (y in 0 until BOARD_HEIGHT)
                    add(Point(x, y))
        }.filter { it !in snake && it !in exclude }
        return all.random()
    }
}

// ── Colors ────────────────────────────────────────────────────────────────────

object GameColors {
    val Background  = Color(0xFF0A0F0A)
    val Grid        = Color(0xFF111811)
    val SnakeHead   = Color(0xFF00FF41)
    val SnakeBody   = Color(0xFF00CC33)
    val SnakeTail   = Color(0xFF007A1E)
    val Food        = Color(0xFFFF3B30)
    val BonusFood   = Color(0xFFFFCC00)
    val PowerFood   = Color(0xFFFF6B00)
    val Wall        = Color(0xFF1A2E1A)
    val ScoreText   = Color(0xFF00FF41)
    val GridLine    = Color(0xFF0D150D)
    val PowerUp     = Color(0xFF5856D6)
}

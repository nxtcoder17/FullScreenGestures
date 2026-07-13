package me.nxtcoder17.fsg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.nxtcoder17.fsg.service.GestureAccessibilityService
import me.nxtcoder17.fsg.ui.theme.FullScreenGesturesTheme

class MainActivity : ComponentActivity() {
    private val isServiceRunning = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FullScreenGesturesTheme {
                FullScreenGesturesApp(
                    isServiceRunning = isServiceRunning.value,
                    onRequestEnableService = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceRunning.value = GestureAccessibilityService.isRunning
    }
}

@Composable
fun rememberPreferenceState(
    key: String,
    defaultValue: Boolean,
    context: Context = LocalContext.current
): MutableState<Boolean> {
    val prefs = remember { context.getSharedPreferences("fsg_settings", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    
    return remember(key) {
        object : MutableState<Boolean> by state {
            override var value: Boolean
                get() = state.value
                set(value) {
                    state.value = value
                    prefs.edit().putBoolean(key, value).apply()
                }
        }
    }
}

@Composable
fun rememberIntPreferenceState(
    key: String,
    defaultValue: Int,
    context: Context = LocalContext.current
): MutableState<Int> {
    val prefs = remember { context.getSharedPreferences("fsg_settings", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getInt(key, defaultValue)) }
    
    return remember(key) {
        object : MutableState<Int> by state {
            override var value: Int
                get() = state.value
                set(value) {
                    state.value = value
                    prefs.edit().putInt(key, value).apply()
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullScreenGesturesApp(
    isServiceRunning: Boolean,
    onRequestEnableService: () -> Unit
) {
    var leftEnabled by rememberPreferenceState("left_edge_enabled", true)
    var rightEnabled by rememberPreferenceState("right_edge_enabled", true)
    var bottomEnabled by rememberPreferenceState("bottom_edge_enabled", true)

    var leftThickness by rememberIntPreferenceState("left_edge_thickness", 15)
    var rightThickness by rememberIntPreferenceState("right_edge_thickness", 15)
    var bottomThickness by rememberIntPreferenceState("bottom_edge_thickness", 15)

    var sensitivity by rememberIntPreferenceState("gesture_sensitivity", 40)
    var hapticsEnabled by rememberPreferenceState("haptics_enabled", true)
    var visualFeedbackEnabled by rememberPreferenceState("visual_feedback_enabled", true)

    val defaultColorInt = remember { android.graphics.Color.parseColor("#B06650A4") }
    var gestureColorInt by rememberIntPreferenceState("gesture_color", defaultColorInt)

    val scrollState = rememberScrollState()

    // Premium Color Palette
    val darkSlate = Color(0xFF0F172A)
    val cardBackground = Color(0xFF1E293B)
    val accentPurple = Color(gestureColorInt)
    val accentTeal = Color(0xFF10B981)
    val accentRed = Color(0xFFF43F5E)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkSlate)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Full Screen Gestures",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Edge Navigation Enabler",
                    fontSize = 14.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Status Card
            StatusCard(
                isServiceRunning = isServiceRunning,
                accentTeal = accentTeal,
                accentRed = accentRed,
                cardBackground = cardBackground,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onRequestEnableService = onRequestEnableService
            )

            // Preview & Edge Visualizer
            PreviewSection(
                leftThickness = leftThickness,
                rightThickness = rightThickness,
                bottomThickness = bottomThickness,
                leftEnabled = leftEnabled,
                rightEnabled = rightEnabled,
                bottomEnabled = bottomEnabled,
                cardBackground = cardBackground,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                gestureColor = accentPurple
            )

            // Gesture Color Accent Selector Card
            ColorCustomizationCard(
                cardBackground = cardBackground,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                selectedColorInt = gestureColorInt,
                onColorSelected = { gestureColorInt = it }
            )

            // Settings Controls
            SettingsSection(
                leftEnabled = leftEnabled,
                onLeftEnabledChange = { leftEnabled = it },
                leftThickness = leftThickness,
                onLeftThicknessChange = { leftThickness = it },
                rightEnabled = rightEnabled,
                onRightEnabledChange = { rightEnabled = it },
                rightThickness = rightThickness,
                onRightThicknessChange = { rightThickness = it },
                bottomEnabled = bottomEnabled,
                onBottomEnabledChange = { bottomEnabled = it },
                bottomThickness = bottomThickness,
                onBottomThicknessChange = { bottomThickness = it },
                sensitivity = sensitivity,
                onSensitivityChange = { sensitivity = it },
                hapticsEnabled = hapticsEnabled,
                onHapticsChange = { hapticsEnabled = it },
                visualFeedbackEnabled = visualFeedbackEnabled,
                onVisualFeedbackChange = { visualFeedbackEnabled = it },
                cardBackground = cardBackground,
                accentPurple = accentPurple,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // Footer / Guide Info
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ℹ️ How it works",
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Due to system limitations, full-screen gestures are often disabled when using a custom launcher. This app runs an Accessibility Service that overlays invisible touch areas on the screen edges to capture gestures (Back, Home, Recents) and execute them seamlessly.",
                        color = textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isServiceRunning: Boolean,
    accentTeal: Color,
    accentRed: Color,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    onRequestEnableService: () -> Unit
) {
    // Pulse animation for status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowColor = if (isServiceRunning) accentTeal else accentRed

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.3f), Color.Transparent)
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pulsing dot
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(glowColor.copy(alpha = 0.3f * scale))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(glowColor)
                    )
                }

                Text(
                    text = if (isServiceRunning) "Gestures Active" else "Service Inactive",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            Text(
                text = if (isServiceRunning)
                    "The gestures service is running. Swipe from the edges of your screen to navigate."
                else
                    "To start using gestures on your custom launcher, you must enable the accessibility service.",
                color = textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Button(
                onClick = onRequestEnableService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) cardBackground else glowColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isServiceRunning) textSecondary.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = if (isServiceRunning) "Configure Accessibility" else "Enable in Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PreviewSection(
    leftThickness: Int,
    rightThickness: Int,
    bottomThickness: Int,
    leftEnabled: Boolean,
    rightEnabled: Boolean,
    bottomEnabled: Boolean,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    gestureColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Interactive Edge Map",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            
            Text(
                text = "Adjust thickness sliders below. The colored zones show active trigger boundaries.",
                fontSize = 12.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF475569), RoundedCornerShape(24.dp))
                    .padding(3.dp)
            ) {
                // Inner screen container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF020617))
                ) {
                    // Wallpaper Mock
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("10:00", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                            Text("🔋 95%", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                        }

                        // App logo mockup
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(gestureColor.copy(alpha = 0.2f))
                                    .border(1.dp, gestureColor, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✨", color = Color.White, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Custom Launcher", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    // Left Edge indicator
                    if (leftEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width((leftThickness * 0.75f).dp) // Scale down for preview display
                                .align(Alignment.CenterStart)
                                .background(gestureColor.copy(alpha = 0.4f))
                        )
                    }

                    // Right Edge indicator
                    if (rightEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width((rightThickness * 0.75f).dp)
                                .align(Alignment.CenterEnd)
                                .background(gestureColor.copy(alpha = 0.4f))
                        )
                    }

                    // Bottom Edge indicator
                    if (bottomEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((bottomThickness * 0.75f).dp)
                                .align(Alignment.BottomCenter)
                                .background(gestureColor.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    leftEnabled: Boolean,
    onLeftEnabledChange: (Boolean) -> Unit,
    leftThickness: Int,
    onLeftThicknessChange: (Int) -> Unit,
    rightEnabled: Boolean,
    onRightEnabledChange: (Boolean) -> Unit,
    rightThickness: Int,
    onRightThicknessChange: (Int) -> Unit,
    bottomEnabled: Boolean,
    onBottomEnabledChange: (Boolean) -> Unit,
    bottomThickness: Int,
    onBottomThicknessChange: (Int) -> Unit,
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    visualFeedbackEnabled: Boolean,
    onVisualFeedbackChange: (Boolean) -> Unit,
    cardBackground: Color,
    accentPurple: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Gesture Preferences",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            // Left Edge Controls
            EdgeControlItem(
                title = "Left Edge (Back)",
                enabled = leftEnabled,
                onEnabledChange = onLeftEnabledChange,
                thickness = leftThickness,
                onThicknessChange = onLeftThicknessChange,
                accentColor = accentPurple,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))

            // Right Edge Controls
            EdgeControlItem(
                title = "Right Edge (Back)",
                enabled = rightEnabled,
                onEnabledChange = onRightEnabledChange,
                thickness = rightThickness,
                onThicknessChange = onRightThicknessChange,
                accentColor = accentPurple,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))

            // Bottom Edge Controls
            EdgeControlItem(
                title = "Bottom Edge (Home & Recents)",
                enabled = bottomEnabled,
                onEnabledChange = onBottomEnabledChange,
                thickness = bottomThickness,
                onThicknessChange = onBottomThicknessChange,
                accentColor = accentPurple,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))

            // Sensitivity Control
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Swipe Sensitivity", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Swipe distance to trigger actions", color = textSecondary, fontSize = 11.sp)
                    }
                    Text("${sensitivity} dp", color = accentPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = { onSensitivityChange(it.toInt()) },
                    valueRange = 20f..80f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentPurple,
                        thumbColor = accentPurple
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))

            // Behavioral Toggles
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Haptic Feedback", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Vibrate when crossing trigger threshold", color = textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = onHapticsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentPurple
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Visual Animation Feedback", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Show edge Bezier bulge and arrow on drag", color = textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = visualFeedbackEnabled,
                        onCheckedChange = onVisualFeedbackChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentPurple
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun EdgeControlItem(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    thickness: Int,
    onThicknessChange: (Int) -> Unit,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (enabled) "Active" else "Disabled", color = if (enabled) accentColor else textSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }

        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Touch Area Width", color = textSecondary, fontSize = 12.sp)
                Text("${thickness} dp", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = thickness.toFloat(),
                onValueChange = { onThicknessChange(it.toInt()) },
                valueRange = 8f..35f,
                colors = SliderDefaults.colors(
                    activeTrackColor = accentColor,
                    thumbColor = accentColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ColorCustomizationCard(
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    selectedColorInt: Int,
    onColorSelected: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎨 Active Edge Color Accent",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            
            Text(
                text = "Drag or tap on the rainbow slider below to customize the color of your edge swipe animations.",
                fontSize = 12.sp,
                color = textSecondary
            )

            val hsv = remember(selectedColorInt) {
                FloatArray(3).apply {
                    android.graphics.Color.colorToHSV(selectedColorInt, this)
                }
            }
            val currentHue = hsv[0]
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                val newHue = fraction * 360f
                                val color = android.graphics.Color.HSVToColor(176, floatArrayOf(newHue, 0.95f, 0.95f))
                                onColorSelected(color)
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                val newHue = fraction * 360f
                                val color = android.graphics.Color.HSVToColor(176, floatArrayOf(newHue, 0.95f, 0.95f))
                                onColorSelected(color)
                            }
                        }
                ) {
                    val colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(colors),
                        size = size
                    )
                    
                    val thumbX = (currentHue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2f)
                    )
                    drawCircle(
                        color = Color(selectedColorInt),
                        radius = 5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                }
            }
        }
    }
}
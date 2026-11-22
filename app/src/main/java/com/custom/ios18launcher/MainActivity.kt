package com.custom.ios18launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Aktifkan Hardware Real-Time Window Blur di Android 12+ / Android 15 (Adreno 725)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 40
            }
        }

        setContent {
            IOS18LauncherScreen()
        }
    }
}

@Composable
fun IOS18LauncherScreen() {
    val context = LocalContext.current
    val allApps = remember { AppRepository.loadInstalledApps(context) }

    // Bagi aplikasi per halaman (20 ikon per halaman: 4 kolom x 5 baris ala iOS 18)
    val pagedApps = remember(allApps) { allApps.chunked(20) }
    val pageCount = (pagedApps.size + 1).coerceAtLeast(2) // +1 halaman terakhir untuk App Library
    val pagerState = rememberPagerState(pageCount = { pageCount })

    var showControlCenter by remember { mutableStateOf(false) }
    var showSpotlight by remember { mutableStateOf(false) }
    var dynamicIslandExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = showControlCenter || showSpotlight || dynamicIslandExpanded) {
        showControlCenter = false
        showSpotlight = false
        dynamicIslandExpanded = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    // Swipe turun dari atas -> buka Control Center / Spotlight
                    if (dragAmount > 25f && !showControlCenter && !showSpotlight) {
                        if (change.position.x > size.width * 0.55f) {
                            showControlCenter = true
                        } else {
                            showSpotlight = true
                        }
                    }
                    // Swipe naik -> tutup overlay
                    if (dragAmount < -25f) {
                        showControlCenter = false
                        showSpotlight = false
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Dynamic Island Pill Interactive
            DynamicIslandHeader(
                expanded = dynamicIslandExpanded,
                onToggle = { dynamicIslandExpanded = !dynamicIslandExpanded }
            )

            // 2. Pager Utama (Home Screen Pages + App Library di halaman paling kanan)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page < pagedApps.size) {
                    HomeScreenPage(
                        apps = pagedApps[page],
                        showWidgets = page == 0,
                        onAppClick = { launchApp(context, it.packageName) }
                    )
                } else {
                    AppLibraryPage(
                        allApps = allApps,
                        onAppClick = { launchApp(context, it.packageName) }
                    )
                }
            }

            // 3. Page Indicator Dots & Search Pill ala iOS 18
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable { showSpotlight = true }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (pagerState.currentPage == pageCount - 1) "Library" else "Search",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Floating Glassmorphic Dock (4 Ikon Utama)
            IOSDock(
                apps = allApps.take(4),
                onAppClick = { launchApp(context, it.packageName) }
            )
        }

        // 5. Overlay Spotlight Search (Swipe Down Kiri/Tengah)
        AnimatedVisibility(
            visible = showSpotlight,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 }
        ) {
            SpotlightOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                apps = allApps,
                onAppClick = {
                    showSpotlight = false
                    searchQuery = ""
                    launchApp(context, it.packageName)
                },
                onDismiss = {
                    showSpotlight = false
                    searchQuery = ""
                }
            )
        }

        // 6. Overlay Control Center iOS 18 (Swipe Down Pojok Kanan Atas)
        AnimatedVisibility(
            visible = showControlCenter,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            ControlCenterOverlay(
                onDismiss = { showControlCenter = false }
            )
        }
    }
}

@Composable
fun DynamicIslandHeader(expanded: Boolean, onToggle: () -> Unit) {
    val width by animateFloatAsState(
        targetValue = if (expanded) 340f else 126f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "islandWidth"
    )
    val height by animateFloatAsState(
        targetValue = if (expanded) 92f else 34f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "islandHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(if (expanded) 28.dp else 50.dp))
                .background(Color.Black)
                .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(if (expanded) 28.dp else 50.dp))
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF30D158),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "iOS 18 UI",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("System Audio Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Snapdragon 7+ Gen 2 • 120Hz", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreenPage(
    apps: List<AppItem>,
    showWidgets: Boolean,
    onAppClick: (AppItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        if (showWidgets) {
            IOSWidgetsRow()
            Spacer(modifier = Modifier.height(18.dp))
        }

        val visibleApps = if (showWidgets) apps.take(12) else apps
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(visibleApps) { app ->
                IOSAppIcon(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

@Composable
fun IOSWidgetsRow() {
    val dateFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dayNumFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val now = remember { Date() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Calendar Widget ala iOS
        Box(
            modifier = Modifier
                .weight(1f)
                .height(148.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = dateFormat.format(now).uppercase(),
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dayNumFormat.format(now),
                    color = Color.Black,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "No more events today",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        }

        // Weather / System Status Widget ala iOS
        Box(
            modifier = Modifier
                .weight(1f)
                .height(148.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C64D1), Color(0xFF4DA0FF))
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Text("Cianjur", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("27°", color = Color.White, fontWeight = FontWeight.Light, fontSize = 40.sp)
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color(0xFFFFD60A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cerah Berawan", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun IOSAppIcon(app: AppItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconBounce"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Squircle Icon Container ala iOS (Corner Radius ~24%)
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = app.icon),
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun IOSDock(apps: List<AppItem>, onAppClick: (AppItem) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.24f))
            .border(0.6.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onAppClick(app) }
                ) {
                    Image(
                        painter = rememberDrawablePainter(drawable = app.icon),
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun AppLibraryPage(allApps: List<AppItem>, onAppClick: (AppItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allApps) {
        if (query.isBlank()) allApps else allApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("App Library", color = Color.White.copy(alpha = 0.7f)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered) { app ->
                IOSAppIcon(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

@Composable
fun SpotlightOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onDismiss: () -> Unit
) {
    val results = remember(query, apps) {
        if (query.isBlank()) apps.take(8) else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Spotlight Search", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(results) { app ->
                    IOSAppIcon(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
fun ControlCenterOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var wifiOn by remember { mutableStateOf(true) }
    var btOn by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0.75f) }
    var volume by remember { mutableFloatStateOf(0.6f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss)
            .statusBarsPadding()
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Control Center", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Connectivity & Media Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 2x2 Connectivity Module
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(155.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            ControlCircleButton(
                                icon = Icons.Rounded.FlightTakeoff,
                                active = false,
                                activeColor = Color(0xFFFF9F0A),
                                onClick = { context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                            )
                            ControlCircleButton(
                                icon = Icons.Rounded.CellTower,
                                active = true,
                                activeColor = Color(0xFF30D158),
                                onClick = { context.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            ControlCircleButton(
                                icon = Icons.Rounded.Wifi,
                                active = wifiOn,
                                activeColor = Color(0xFF0A84FF),
                                onClick = {
                                    wifiOn = !wifiOn
                                    context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
                                }
                            )
                            ControlCircleButton(
                                icon = Icons.Rounded.Bluetooth,
                                active = btOn,
                                activeColor = Color(0xFF0A84FF),
                                onClick = {
                                    btOn = !btOn
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            )
                        }
                    }
                }

                // Media Widget Module
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(155.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Now Playing", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text("iOS 18 Audio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = null, tint = Color.White)
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                            Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            // Brightness & Volume Vertical Pill Sliders ala iOS 18
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VerticalPillSlider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    icon = Icons.Rounded.BrightnessHigh,
                    modifier = Modifier.weight(1f)
                )
                VerticalPillSlider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
                    },
                    icon = Icons.Rounded.VolumeUp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom Quick Toggles (Flashlight, Timer, Calculator, Camera)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ControlCircleButton(
                    icon = Icons.Rounded.FlashlightOn,
                    active = torchOn,
                    activeColor = Color(0xFF0A84FF),
                    onClick = {
                        torchOn = !torchOn
                        toggleTorch(context, torchOn)
                    }
                )
                ControlCircleButton(
                    icon = Icons.Rounded.Timer,
                    active = false,
                    activeColor = Color(0xFFFF9F0A),
                    onClick = { context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }
                )
                ControlCircleButton(
                    icon = Icons.Rounded.Settings,
                    active = false,
                    activeColor = Color.Gray,
                    onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                )
                ControlCircleButton(
                    icon = Icons.Rounded.CameraAlt,
                    active = false,
                    activeColor = Color.Gray,
                    onClick = { context.startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
                )
            }
        }
    }
}

@Composable
fun ControlCircleButton(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) activeColor else Color.White.copy(alpha = 0.18f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun VerticalPillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount / size.height
                    onValueChange((value + delta).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value)
                .background(Color.White.copy(alpha = 0.9f))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (value > 0.25f) Color.Black else Color.White,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .size(26.dp)
        )
    }
}

private fun launchApp(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    }
}

private fun toggleTorch(context: Context, enable: Boolean) {
    try {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = camManager.cameraIdList.firstOrNull() ?: return
        camManager.setTorchMode(camId, enable)
    } catch (_: Exception) {
    }
}

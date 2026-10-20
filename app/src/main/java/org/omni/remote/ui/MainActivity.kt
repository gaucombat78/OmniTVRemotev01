package org.omni.remote.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omni.remote.core.model.RemoteCommand
import org.omni.remote.driver.SupportedApp
import org.omni.remote.voice.VoiceStatus

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    RemoteScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val state by viewModel.uiState.collectAsState()
    val voiceStatus by viewModel.voiceEngine.status.collectAsState()
    var hasMicPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header: Connection Bar & Power ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = state.connectedTv?.name ?: "No TV Connected",
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = state.connectionStatus,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            IconButton(
                onClick = { viewModel.send(RemoteCommand.POWER) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Discovered Devices Row (Shows all nearby options) ---
        if (state.connectedTv == null) {
            Text("Available TV Options:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                items(state.discoveredDevices) { tv ->
                    Button(
                        onClick = { viewModel.connectToTv(tv) },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Text(tv.name)
                    }
                }
            }
        }

        // --- Quick App Dock (Includes Amazon Prime Video, Netflix, YouTube, Spotify) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppShortcutButton("Prime", Color(0xFF00A8E1)) {
                viewModel.launchAppAndSearch(SupportedApp.PRIME_VIDEO, "")
            }
            AppShortcutButton("Netflix", Color(0xFFE50914)) {
                viewModel.launchAppAndSearch(SupportedApp.NETFLIX, "")
            }
            AppShortcutButton("YouTube", Color(0xFFFF0000)) {
                viewModel.launchAppAndSearch(SupportedApp.YOUTUBE, "")
            }
            AppShortcutButton("Spotify", Color(0xFF1DB954)) {
                viewModel.launchAppAndSearch(SupportedApp.SPOTIFY, "")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Navigation: Tactile D-Pad or Touchpad ---
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (state.isTouchpadMode) {
                TouchpadSurface(
                    onSwipe = { cmd -> viewModel.send(cmd) },
                    onTap = { viewModel.send(RemoteCommand.OK) }
                )
            } else {
                TactileDPad(
                    onUp = { viewModel.send(RemoteCommand.UP) },
                    onDown = { viewModel.send(RemoteCommand.DOWN) },
                    onLeft = { viewModel.send(RemoteCommand.LEFT) },
                    onRight = { viewModel.send(RemoteCommand.RIGHT) },
                    onOk = { viewModel.send(RemoteCommand.OK) }
                )
            }
        }

        // --- Mode Switch & System Keys ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { viewModel.send(RemoteCommand.BACK) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            IconButton(onClick = { viewModel.send(RemoteCommand.HOME) }) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
            }
            IconButton(onClick = { viewModel.togglePadMode() }) {
                Icon(
                    if (state.isTouchpadMode) Icons.Default.Gamepad else Icons.Default.TouchApp,
                    contentDescription = "Toggle Pad",
                    tint = Color.Cyan
                )
            }
        }

        // --- Volume & Media Controls ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.send(RemoteCommand.VOL_DOWN) }) { Text("VOL -") }
            IconButton(onClick = { viewModel.send(RemoteCommand.MUTE) }) {
                Icon(Icons.Default.VolumeMute, contentDescription = "Mute", tint = Color.White)
            }
            Button(onClick = { viewModel.send(RemoteCommand.VOL_UP) }) { Text("VOL +") }
        }

        // --- Live Voice Status Overlay ---
        AnimatedVisibility(visible = voiceStatus !is VoiceStatus.Idle) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
            ) {
                Text(
                    text = when (val s = voiceStatus) {
                        is VoiceStatus.Listening -> "Listening... speak now"
                        is VoiceStatus.Recognizing -> "Recognizing: \"${s.partialText}\""
                        is VoiceStatus.Success -> "Executed!"
                        is VoiceStatus.Error -> s.message
                        else -> ""
                    },
                    modifier = Modifier.padding(12.dp),
                    color = Color.Yellow,
                    fontSize = 14.sp
                )
            }
        }

        // --- Voice Mic Button (Bottom Dock) ---
        FloatingActionButton(
            onClick = {
                if (hasMicPermission) {
                    viewModel.voiceEngine.startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            containerColor = Color(0xFF6200EE),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Hold to Speak", tint = Color.White)
        }
    }
}

@Composable
fun TactileDPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(Color(0xFF212121)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onUp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
        }
        IconButton(onClick = onDown, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
        }
        IconButton(onClick = onLeft, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White)
        }
        IconButton(onClick = onRight, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White)
        }
        Button(
            onClick = onOk,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
        ) {
            Text("OK", color = Color.White)
        }
    }
}

@Composable
fun TouchpadSurface(onSwipe: (RemoteCommand) -> Unit, onTap: () -> Unit) {
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    val threshold = 90f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.Gray, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                            if (totalDragX > threshold) onSwipe(RemoteCommand.RIGHT)
                            else if (totalDragX < -threshold) onSwipe(RemoteCommand.LEFT)
                        } else {
                            if (totalDragY > threshold) onSwipe(RemoteCommand.DOWN)
                            else if (totalDragY < -threshold) onSwipe(RemoteCommand.UP)
                        }
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("Swipe to Navigate • Tap center for OK", color = Color.DarkGray)
    }
}

@Composable
fun AppShortcutButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}

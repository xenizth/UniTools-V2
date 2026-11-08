package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import com.example.ui.UniToolsViewModel
import com.example.ui.components.ConsoleBottomSheet
import com.example.ui.components.HardwareGuidanceDialog
import com.example.ui.components.HeaderConfigCard
import com.example.ui.components.UsbStatusPill
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.FlashingScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.components.KernelSuNavigationBar
import com.example.ui.components.NavigationItem
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: UniToolsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "UniTools",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            ),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.statusBarsPadding()
                        )
                    },
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            KernelSuNavigationBar(
                                items = listOf(
                                    NavigationItem(
                                        title = "Flashing",
                                        icon = Icons.Default.FlashOn,
                                        selected = uiState.activeTab == 0,
                                        onClick = { viewModel.setTab(0) }
                                    ),
                                    NavigationItem(
                                        title = "Backup",
                                        icon = Icons.Default.Backup,
                                        selected = uiState.activeTab == 1,
                                        onClick = { viewModel.setTab(1) }
                                    ),
                                    NavigationItem(
                                        title = "Tools",
                                        icon = Icons.Default.Build,
                                        selected = uiState.activeTab == 2,
                                        onClick = { viewModel.setTab(2) }
                                    ),
                                    NavigationItem(
                                        title = "Files",
                                        icon = Icons.Default.Folder,
                                        selected = uiState.activeTab == 3,
                                        onClick = { viewModel.setTab(3) }
                                    )
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainContentColumn(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            ConsoleBottomSheet(
                                logs = uiState.logs,
                                isExecuting = uiState.isExecuting,
                                currentTask = uiState.currentTaskDescription,
                                isExpanded = uiState.isConsoleExpanded,
                                onToggleExpand = { viewModel.toggleConsoleExpanded(it) },
                                onClearLogs = { viewModel.clearLogs() },
                                onCancelTask = { viewModel.cancelOperation() }
                            )
                        }
                    }
                }

                if (uiState.showHardwareDialog) {
                    HardwareGuidanceDialog(
                        actionName = uiState.pendingActionName,
                        onConfirm = { viewModel.confirmHardwareDialog() },
                        onDismiss = { viewModel.dismissHardwareDialog() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContentColumn(
    uiState: com.example.ui.UniToolsUiState,
    viewModel: UniToolsViewModel,
    modifier: Modifier = Modifier
) {
    var isHeaderVisible by remember { mutableStateOf(true) }

    // Reset visibility when switching tabs
    LaunchedEffect(uiState.activeTab) {
        isHeaderVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -12f) {
                    // Scrolling down: hide target configuration
                    isHeaderVisible = false
                } else if (delta > 12f) {
                    // Scrolling up: show target configuration
                    isHeaderVisible = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // When pulled past top boundary, reveal header
                if (available.y > 0) {
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 1200.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        // USB Auto-Detect status pill
        UsbStatusPill(
            usbStatus = uiState.usbStatus,
            onRefresh = { viewModel.refreshUsb() }
        )

        // Target SoC and Device configuration header card (collapses when scrolling down)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            HeaderConfigCard(
                uiState = uiState,
                binariesDir = viewModel.binaryManager.binariesDir,
                onSelectSoc = { viewModel.selectSoc(it) },
                onSelectBrand = { viewModel.selectBrand(it) },
                onToggleHexMode = { viewModel.toggleHexMode() },
                onSelectRebootMode = { viewModel.selectRebootMode(it) }
            )
        }

        // Active tab screens
        when (uiState.activeTab) {
            0 -> FlashingScreen(
                uiState = uiState,
                onUnlockBootloader = { viewModel.executeUnlockBootloader() },
                onAddFlashTask = { part, path, size -> viewModel.addFlashTask(part, path, size) },
                onRemoveFlashTask = { viewModel.removeFlashTask(it) },
                onClearFlashTasks = { viewModel.clearFlashTasks() },
                onFlashAll = { viewModel.executeFlashTasks() }
            )
            1 -> BackupRestoreScreen(
                uiState = uiState,
                onSelectPreset = { viewModel.selectBackupPreset(it) },
                onTogglePartition = { viewModel.togglePartitionSelection(it) },
                onStartBackup = { viewModel.executeBackup() },
                onStartRestore = { viewModel.executeRestore(it) }
            )
            2 -> ToolsScreen(
                uiState = uiState,
                onWipeUserdata = { viewModel.executeWipeUserdata() },
                onDisableVerity = { viewModel.executeDisableVerity() },
                onResetFrp = { viewModel.executeResetFrp() },
                onSwitchSlot = { viewModel.executeSwitchSlot(it) },
                onReboot = { viewModel.executeReboot(it) }
            )
            3 -> FilesScreen(
                uiState = uiState,
                binariesDir = viewModel.binaryManager.binariesDir,
                onOpenDirectory = { viewModel.openDirectory(it) },
                onNavigateBack = { viewModel.navigateFilesBack() },
                onResetNav = { viewModel.resetFilesNavigation() },
                onImportFiles = { uris, dir -> viewModel.importFilesToDirectory(uris, dir) },
                onCreateFolder = { dir, name -> viewModel.createFolder(dir, name) },
                onRenameFile = { file, name -> viewModel.renameFile(file, name) },
                onDeleteFile = { file -> viewModel.deleteFile(file) },
                onToggleExecutable = { file -> viewModel.toggleFileExecutable(file) }
            )
        }
    }
}

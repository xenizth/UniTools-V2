package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.executor.ExecutionResult
import com.example.executor.LogEntry
import com.example.executor.RootExecutor
import com.example.manager.BinaryFileManager
import com.example.manager.UsbDetector
import com.example.manager.UsbStatus
import com.example.model.BackupPreset
import com.example.model.DeviceBrand
import com.example.model.FlashTaskItem
import com.example.model.HexMode
import com.example.model.PartitionCatalog
import com.example.model.RebootMode
import com.example.model.SocType
import com.example.model.UniToolsConfig
import com.example.repository.UniToolsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class FilesNavMode {
    BROWSER
}

data class UniToolsUiState(
    val isRootGranted: Boolean? = null,
    val isBinariesReady: Boolean = false,
    val selectedSoc: SocType = SocType.UMS9230,
    val selectedBrand: DeviceBrand = DeviceBrand.INFINIX,
    val hexMode: HexMode = HexMode.STANDARD,
    val rebootMode: RebootMode = RebootMode.SYSTEM,
    val isExecuting: Boolean = false,
    val currentTaskDescription: String = "",
    val activeTab: Int = 0,
    val flashTasks: List<FlashTaskItem> = emptyList(),
    val selectedBackupPartitions: Set<String> = PartitionCatalog.IMEI_PARTITION_NAMES.toSet(),
    val activeBackupPreset: BackupPreset = BackupPreset.IMEI_NVRAM,
    val logs: List<LogEntry> = emptyList(),
    val isConsoleExpanded: Boolean = false,
    val showHardwareDialog: Boolean = false,
    val pendingActionName: String = "",
    val pendingActionRunnable: (() -> Unit)? = null,
    val usbStatus: UsbStatus = UsbStatus(),
    // Files Tab state
    val filesNavMode: FilesNavMode = FilesNavMode.BROWSER,
    val currentFileDir: File? = null,
    val fileRefreshTrigger: Int = 0,
    // Configuration from UniTools Configurations Center
    val config: UniToolsConfig = UniToolsConfig()
)

class UniToolsViewModel(application: Application) : AndroidViewModel(application) {
    val binaryManager = BinaryFileManager(application)
    private val rootExecutor = RootExecutor()
    val repository = UniToolsRepository(binaryManager, rootExecutor)
    private val usbDetector = UsbDetector(application)

    private val _uiState = MutableStateFlow(UniToolsUiState())
    val uiState: StateFlow<UniToolsUiState> = _uiState.asStateFlow()

    init {
        loadConfiguration()
        checkSystem()
        observeLogs()
        observeUsb()
    }

    private fun loadConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // The bundled config.txt (app/src/main/assets/config.txt) is this fork's own
                // feature gates and is authoritative. We intentionally do NOT fetch/apply
                // Classity-Real's upstream UniTools-Configurations repo here anymore: that repo
                // is outside this fork's control, and it ships with [Unlock Bootloader] - X —
                // every launch was silently re-disabling the Unlock Bootloader button a few
                // hundred milliseconds after this screen appeared, overwriting whatever this
                // fork's own config.txt said, with no indication to the user that anything had
                // changed. If you want to track an upstream config again, point this at a
                // config.txt you control instead.
                val app = getApplication<Application>()
                val localContent = app.assets.open("config.txt").bufferedReader().use { it.readText() }
                val localConfig = UniToolsConfig.parseFromText(localContent)
                _uiState.update { it.copy(config = localConfig) }
            } catch (e: Exception) {
                // Keep default
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        usbDetector.stopListening()
    }

    private fun checkSystem() {
        viewModelScope.launch {
            val root = repository.checkRoot()
            _uiState.update { it.copy(isRootGranted = root) }

            // Extract binaries silently without setting status banner
            repository.initializeBinaries {}
            _uiState.update { it.copy(isBinariesReady = true) }
            repository.prepareTargetFdl(_uiState.value.selectedSoc, _uiState.value.selectedBrand)
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            repository.logs.collect { newEntry ->
                _uiState.update { state ->
                    val updated = state.logs + newEntry
                    val trimmed = if (updated.size > 400) updated.takeLast(400) else updated
                    state.copy(logs = trimmed)
                }
            }
        }
    }

    private fun observeUsb() {
        usbDetector.startListening()
        viewModelScope.launch {
            usbDetector.status.collect { status ->
                _uiState.update { state ->
                    var soc = state.selectedSoc
                    if (status.isUnisocBrom) {
                        // Keep or match active Unisoc SoC
                    }
                    state.copy(usbStatus = status, selectedSoc = soc)
                }
            }
        }
    }

    fun refreshUsb() {
        usbDetector.checkConnectedDevices()
    }

    fun requestUsbPermission() {
        usbDetector.requestPermission()
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(activeTab = index) }
    }

    fun selectSoc(soc: SocType) {
        val availableBrands = DeviceBrand.getAvailableBrands(binaryManager.binariesDir, soc.id)
        val newBrand = if (availableBrands.contains(_uiState.value.selectedBrand)) {
            _uiState.value.selectedBrand
        } else {
            availableBrands.firstOrNull() ?: DeviceBrand.INFINIX
        }
        _uiState.update { it.copy(selectedSoc = soc, selectedBrand = newBrand) }
        repository.prepareTargetFdl(soc, newBrand)
    }

    fun selectBrand(brand: DeviceBrand) {
        _uiState.update { it.copy(selectedBrand = brand) }
        repository.prepareTargetFdl(_uiState.value.selectedSoc, brand)
    }

    fun toggleHexMode() {
        _uiState.update {
            val next = if (it.hexMode == HexMode.STANDARD) HexMode.ALTERNATIVE else HexMode.STANDARD
            it.copy(hexMode = next)
        }
    }

    fun selectRebootMode(mode: RebootMode) {
        _uiState.update { it.copy(rebootMode = mode) }
    }

    fun toggleConsoleExpanded(expanded: Boolean? = null) {
        _uiState.update { it.copy(isConsoleExpanded = expanded ?: !it.isConsoleExpanded) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun addFlashTask(partition: String, filePath: String, sizeStr: String) {
        val newItem = FlashTaskItem(partition, filePath, sizeStr)
        _uiState.update { state ->
            val list = state.flashTasks.filter { it.partitionName != partition } + newItem
            state.copy(flashTasks = list)
        }
    }

    fun removeFlashTask(partition: String) {
        _uiState.update { state ->
            state.copy(flashTasks = state.flashTasks.filter { it.partitionName != partition })
        }
    }

    fun clearFlashTasks() {
        _uiState.update { it.copy(flashTasks = emptyList()) }
    }

    fun selectBackupPreset(preset: BackupPreset) {
        val partitions = when (preset) {
            BackupPreset.IMEI_NVRAM -> PartitionCatalog.IMEI_PARTITION_NAMES.toSet()
            BackupPreset.ALL_LITE -> PartitionCatalog.ALL_LITE_PARTITION_NAMES.toSet()
            BackupPreset.ALL_FULL -> PartitionCatalog.ALL_PARTITION_NAMES.toSet()
            BackupPreset.CUSTOM -> _uiState.value.selectedBackupPartitions
        }
        _uiState.update {
            it.copy(
                activeBackupPreset = preset,
                selectedBackupPartitions = partitions
            )
        }
    }

    fun togglePartitionSelection(name: String) {
        _uiState.update { state ->
            val current = state.selectedBackupPartitions.toMutableSet()
            if (current.contains(name)) current.remove(name) else current.add(name)
            state.copy(
                selectedBackupPartitions = current,
                activeBackupPreset = BackupPreset.CUSTOM
            )
        }
    }

    // Files Tab Navigation
    fun openDirectory(dir: File) {
        _uiState.update {
            it.copy(
                filesNavMode = FilesNavMode.BROWSER,
                currentFileDir = dir
            )
        }
    }

    fun navigateFilesBack() {
        _uiState.update { state ->
            val current = state.currentFileDir
            if (current != null && current != binaryManager.binariesDir && current.parentFile != null && current.parentFile!!.absolutePath.startsWith(binaryManager.binariesDir.absolutePath)) {
                state.copy(currentFileDir = current.parentFile)
            } else {
                state.copy(currentFileDir = null)
            }
        }
    }

    fun resetFilesNavigation() {
        _uiState.update {
            it.copy(
                filesNavMode = FilesNavMode.BROWSER,
                currentFileDir = null
            )
        }
    }

    // File Management Operations
    fun importFilesToDirectory(uris: List<Uri>, targetDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            targetDir.mkdirs()
            uris.forEach { uri ->
                var fileName = "imported_${System.currentTimeMillis()}"
                getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx != -1) {
                        fileName = cursor.getString(nameIdx)
                    }
                }
                val dest = File(targetDir, fileName)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(dest).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
            _uiState.update { it.copy(fileRefreshTrigger = it.fileRefreshTrigger + 1) }
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (newName.isNotBlank() && file.exists()) {
                val dest = File(file.parentFile, newName)
                file.renameTo(dest)
                _uiState.update { it.copy(fileRefreshTrigger = it.fileRefreshTrigger + 1) }
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.deleteRecursively()
                _uiState.update { it.copy(fileRefreshTrigger = it.fileRefreshTrigger + 1) }
            }
        }
    }

    fun createFolder(parentDir: File, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = folderName.trim()
            if (cleanName.isNotBlank()) {
                parentDir.mkdirs()
                val newDir = File(parentDir, cleanName)
                newDir.mkdirs()
                _uiState.update { it.copy(fileRefreshTrigger = it.fileRefreshTrigger + 1) }
            }
        }
    }

    fun toggleFileExecutable(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                val current = file.canExecute()
                file.setExecutable(!current, false)
                _uiState.update { it.copy(fileRefreshTrigger = it.fileRefreshTrigger + 1) }
            }
        }
    }

    // Safety Prompt & Execution Interceptor
    fun promptHardwareExecution(actionName: String, onConfirmed: () -> Unit) {
        _uiState.update {
            it.copy(
                showHardwareDialog = true,
                pendingActionName = actionName,
                pendingActionRunnable = onConfirmed
            )
        }
    }

    fun dismissHardwareDialog() {
        _uiState.update { it.copy(showHardwareDialog = false, pendingActionRunnable = null) }
    }

    fun confirmHardwareDialog() {
        val runnable = _uiState.value.pendingActionRunnable
        _uiState.update { it.copy(showHardwareDialog = false, pendingActionRunnable = null) }
        runnable?.invoke()
    }

    // Operations
    fun executeUnlockBootloader() {
        promptHardwareExecution("Unlock Bootloader") {
            runOperation("Unlocking Bootloader") {
                repository.unlockBootloaderSequence(
                    soc = _uiState.value.selectedSoc,
                    brand = _uiState.value.selectedBrand,
                    hexMode = _uiState.value.hexMode,
                    onStepUpdate = { step ->
                        _uiState.update { it.copy(currentTaskDescription = step) }
                    }
                )
            }
        }
    }

    fun executeFlashTasks() {
        val tasks = _uiState.value.flashTasks
        if (tasks.isEmpty()) return
        promptHardwareExecution("Flash ${tasks.size} Partitions") {
            runOperation("Flashing Partitions") {
                repository.flashPartitions(
                    soc = _uiState.value.selectedSoc,
                    hexMode = _uiState.value.hexMode,
                    tasks = tasks,
                    rebootMode = _uiState.value.rebootMode
                )
            }
        }
    }

    fun executeBackup() {
        val isImei = _uiState.value.activeBackupPreset == BackupPreset.IMEI_NVRAM
        val parts = _uiState.value.selectedBackupPartitions.toList()
        promptHardwareExecution("Backup ${if (isImei) "IMEI / NVRAM" else "${parts.size} Partitions"}") {
            runOperation("Backing up Partitions") {
                repository.backupPartitions(
                    soc = _uiState.value.selectedSoc,
                    hexMode = _uiState.value.hexMode,
                    partitionNames = parts,
                    isImeiBackup = isImei,
                    rebootMode = _uiState.value.rebootMode
                )
            }
        }
    }

    fun executeRestore(backupDir: File) {
        promptHardwareExecution("Restore Partitions") {
            runOperation("Restoring Partitions") {
                repository.restorePartitions(
                    soc = _uiState.value.selectedSoc,
                    hexMode = _uiState.value.hexMode,
                    backupFolder = backupDir,
                    rebootMode = _uiState.value.rebootMode
                )
            }
        }
    }

    fun executeWipeUserdata() {
        promptHardwareExecution("Wipe Userdata (Factory Reset)") {
            runOperation("Wiping Userdata") {
                repository.wipeUserdata(_uiState.value.selectedSoc, _uiState.value.hexMode)
            }
        }
    }

    fun executeDisableVerity() {
        promptHardwareExecution("Disable AVB Verity") {
            runOperation("Disabling Verity") {
                repository.disableVerity(_uiState.value.selectedSoc, _uiState.value.hexMode)
            }
        }
    }

    fun executeResetFrp() {
        promptHardwareExecution("Reset FRP Lock") {
            runOperation("Resetting FRP") {
                repository.resetFrp(_uiState.value.selectedSoc, _uiState.value.hexMode)
            }
        }
    }

    fun executeSwitchSlot(slot: String) {
        promptHardwareExecution("Switch Active Slot to $slot") {
            runOperation("Switching Slot to $slot") {
                repository.switchActiveSlot(_uiState.value.selectedSoc, _uiState.value.hexMode, slot)
            }
        }
    }

    fun executeReboot(mode: RebootMode) {
        promptHardwareExecution("Reboot Device ($mode)") {
            runOperation("Rebooting device") {
                repository.directReboot(_uiState.value.selectedSoc, _uiState.value.hexMode, mode)
            }
        }
    }

    fun cancelOperation() {
        repository.cancelActiveOperation()
        _uiState.update { it.copy(isExecuting = false, currentTaskDescription = "Operation cancelled.") }
    }

    private fun runOperation(description: String, block: suspend () -> ExecutionResult) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExecuting = true,
                    currentTaskDescription = description,
                    isConsoleExpanded = true
                )
            }
            try {
                block()
            } finally {
                _uiState.update { it.copy(isExecuting = false) }
            }
        }
    }
}

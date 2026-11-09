package com.example.repository

import com.example.executor.ExecutionResult
import com.example.executor.LogEntry
import com.example.executor.RootExecutor
import com.example.manager.BinaryFileManager
import com.example.model.DeviceBrand
import com.example.model.FlashTaskItem
import com.example.model.HexMode
import com.example.model.RebootMode
import com.example.model.SocType
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

class UniToolsRepository(
    private val binaryManager: BinaryFileManager,
    private val rootExecutor: RootExecutor
) {
    val logs: SharedFlow<LogEntry> = rootExecutor.logs

    suspend fun checkRoot(): Boolean = rootExecutor.checkRootAccess()

    suspend fun initializeBinaries(onProgress: (String) -> Unit): Boolean {
        return binaryManager.ensureBinariesReady(onProgress)
    }

    fun prepareTargetFdl(soc: SocType, brand: DeviceBrand) {
        binaryManager.prepareFdlFiles(soc, brand)
    }

    fun buildBaseCommand(soc: SocType, hexMode: HexMode): String {
        val noverify = if (hexMode == HexMode.STANDARD) soc.noverifyAddr else soc.altNoverifyAddr
        val fdl1 = soc.fdl1Addr
        val fdl2 = soc.fdl2Addr
        return "./spd_dump exec_addr $noverify fdl fdl1-dl.bin $fdl1 fdl fdl2-dl.bin $fdl2 exec"
    }

    suspend fun executeRawCommand(cmd: String): ExecutionResult {
        return rootExecutor.executeCommand(cmd, binaryManager.binariesDir)
    }

    suspend fun flashPartitions(
        soc: SocType,
        hexMode: HexMode,
        tasks: List<FlashTaskItem>,
        rebootMode: RebootMode
    ): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val flashArgs = tasks.joinToString(" ") { "w ${it.partitionName} \"${it.filePath}\"" }
        val fullCmd = "$base $flashArgs ${rebootMode.flag}"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun backupPartitions(
        soc: SocType,
        hexMode: HexMode,
        partitionNames: List<String>,
        isImeiBackup: Boolean,
        rebootMode: RebootMode
    ): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val backupPath = binaryManager.backupDir.absolutePath
        val fullCmd = if (isImeiBackup) {
            "$base path \"$backupPath\" r miscdata r prodnv r l_fixnv1 r l_fixnv2 r l_runtimenv1 r l_runtimenv2 ${rebootMode.flag}"
        } else {
            val readArgs = partitionNames.joinToString(" ") { "r $it" }
            "$base path \"$backupPath\" $readArgs ${rebootMode.flag}"
        }
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun restorePartitions(
        soc: SocType,
        hexMode: HexMode,
        backupFolder: File,
        rebootMode: RebootMode
    ): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base write_parts \"${backupFolder.absolutePath}\" ${rebootMode.flag}"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun unlockBootloaderSequence(
        soc: SocType,
        brand: DeviceBrand,
        hexMode: HexMode,
        onStepUpdate: (String) -> Unit
    ): ExecutionResult {
        prepareTargetFdl(soc, brand)
        val base = buildBaseCommand(soc, hexMode)
        val noverify = if (hexMode == HexMode.STANDARD) soc.noverifyAddr else soc.altNoverifyAddr
        val fdl1 = soc.fdl1Addr

        // Step 1: Backup & erase splloader
        onStepUpdate("Backing up splloader and uboot...")
        val backupSplDir = binaryManager.backupSplDir.absolutePath
        val step1Cmd = "$base path \"$backupSplDir\" r splloader r uboot e splloader e splloader_bak reset"
        val res1 = rootExecutor.executeCommand(step1Cmd, binaryManager.binariesDir)
        if (!res1.isSuccess) return res1

        // Step 2: Generate spl-unlock
        onStepUpdate("Generating unlock token from splloader...")
        val splFile = File(backupSplDir, "splloader.bin")
        if (!splFile.exists()) {
            // Step 1 was reported successful but never actually produced a splloader dump (e.g.
            // the device dropped off USB mid-read). Generating a token from whatever stale
            // splloader.bin might already be sitting in binariesDir from a previous run would
            // silently create a token for the WRONG device. Stop here instead.
            onStepUpdate("Unlock aborted: no splloader backup was produced in step 1.")
            return ExecutionResult(exitCode = -1, isSuccess = false, durationMs = 0)
        }
        splFile.copyTo(File(binaryManager.binariesDir, "splloader.bin"), overwrite = true)
        val genCmd = "./gen_spl-unlock splloader.bin"
        val res2 = rootExecutor.executeCommand(genCmd, binaryManager.binariesDir)
        if (!res2.isSuccess) return res2

        // Step 3: Write modified uboot
        onStepUpdate("Flashing modified uboot loader...")
        val step3Cmd = "$base w uboot fdl2-cboot.bin reset"
        val res3 = rootExecutor.executeCommand(step3Cmd, binaryManager.binariesDir)
        if (!res3.isSuccess) return res3

        // Step 4: Send bootloader unlocker
        onStepUpdate("Sending bootloader unlocker payload...")
        val step4Cmd = "./spd_dump exec_addr $noverify fdl spl-unlock.bin $fdl1"
        val res4 = rootExecutor.executeCommand(step4Cmd, binaryManager.binariesDir)
        if (!res4.isSuccess) {
            // The unlock payload itself failed to send — do NOT fall through to step 6. Restoring
            // the original splloader now would silently discard the only evidence of what step 4
            // actually did to the device, and would report this whole sequence as however step 6
            // happens to exit rather than as the real failure at step 4.
            onStepUpdate("Unlock payload failed to send — leaving splloader/uboot as-is for inspection.")
            return res4
        }

        // Step 5: Read status from miscdata
        onStepUpdate("Verifying unlock status from miscdata...")
        val step5Cmd = "$base verbose 2 read_part miscdata 8192 64 m.bin reset"
        val res5 = rootExecutor.executeCommand(step5Cmd, binaryManager.binariesDir)
        if (!res5.isSuccess) {
            onStepUpdate("Could not verify miscdata after unlock — check the console log before continuing.")
        }

        // Step 6: Restore splloader and uboot
        onStepUpdate("Restoring original splloader and uboot...")
        val step6Cmd = "$base path \"$backupSplDir\" w splloader splloader.bin w uboot uboot.bin reset"
        return rootExecutor.executeCommand(step6Cmd, binaryManager.binariesDir)
    }

    suspend fun wipeUserdata(soc: SocType, hexMode: HexMode): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base w misc misc-wipe.bin reset"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun disableVerity(soc: SocType, hexMode: HexMode): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base verity 0 reset"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun resetFrp(soc: SocType, hexMode: HexMode): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base r persist e persist reset"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun switchActiveSlot(soc: SocType, hexMode: HexMode, slot: String): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base set_active $slot reset"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    suspend fun directReboot(soc: SocType, hexMode: HexMode, mode: RebootMode): ExecutionResult {
        val base = buildBaseCommand(soc, hexMode)
        val fullCmd = "$base ${mode.flag}"
        return rootExecutor.executeCommand(fullCmd, binaryManager.binariesDir)
    }

    fun cancelActiveOperation() {
        rootExecutor.cancel()
    }
}

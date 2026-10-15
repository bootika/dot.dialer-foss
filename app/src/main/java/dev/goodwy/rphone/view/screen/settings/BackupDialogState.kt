package dev.goodwy.rphone.view.screen.settings

internal sealed interface BackupDialogState {
    data object Idle : BackupDialogState
    data object Restoring : BackupDialogState
    data class BackupSuccess(val path: String) : BackupDialogState
    data object RestoreSuccess : BackupDialogState
    data class Error(val message: String) : BackupDialogState
}

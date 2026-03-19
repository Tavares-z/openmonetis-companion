package br.com.openmonetis.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.openmonetis.companion.data.local.entities.SyncStatus
import br.com.openmonetis.companion.ui.theme.Success

data class CapturedNotificationDetails(
    val appName: String,
    val title: String?,
    val text: String,
    val parsedAmount: String?,
    val parsedName: String?,
    val syncStatus: SyncStatus,
    val timestampFull: String,
    val syncError: String?
)

@Composable
fun CapturedNotificationDetailsDialog(
    notification: CapturedNotificationDetails,
    onDismiss: () -> Unit,
    onCopyOriginalText: () -> Unit,
    onRetry: (() -> Unit)?,
    onDiscard: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalhes do lançamento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailItem("Status", notification.syncStatus.toUserLabel(), notification.syncStatus.toUserColor())
                DetailItem("App", notification.appName)
                DetailItem("Data", notification.timestampFull)
                notification.parsedName?.let { DetailItem("Estabelecimento", it) }
                notification.parsedAmount?.let { DetailItem("Valor", it) }
                notification.title?.let { DetailItem("Título original", it) }
                DetailItem("Texto original", notification.text)
                notification.syncError?.let { DetailItem("Erro", it, MaterialTheme.colorScheme.error) }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCopyOriginalText,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copiar texto")
                    }
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reenviar")
                        }
                    }
                }

                if (onDiscard != null) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Descartar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

private fun SyncStatus.toUserLabel(): String {
    return when (this) {
        SyncStatus.SYNCED, SyncStatus.PROCESSED -> "Enviado"
        SyncStatus.SYNC_FAILED -> "Pendente com erro"
        SyncStatus.SYNCING -> "Enviando"
        SyncStatus.DISCARDED -> "Descartado"
        SyncStatus.PENDING_SYNC -> "Pendente"
    }
}

@Composable
private fun SyncStatus.toUserColor() = when (this) {
    SyncStatus.SYNCED, SyncStatus.PROCESSED -> Success
    SyncStatus.SYNC_FAILED -> MaterialTheme.colorScheme.error
    SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
    SyncStatus.DISCARDED -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncStatus.PENDING_SYNC -> MaterialTheme.colorScheme.onSurface
}

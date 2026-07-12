package br.com.openmonetis.companion.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.openmonetis.companion.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAlertPreference by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshBatteryOptimizationStatus()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingAlertPreference?.invoke(granted)
        pendingAlertPreference = null
        if (!granted) {
            Toast.makeText(
                context,
                "Permissão necessária para exibir alertas do Companion",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun updateAlertPreference(
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onCheckedChange(enabled)
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onCheckedChange(true)
        } else {
            pendingAlertPreference = onCheckedChange
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle disconnect navigation
    LaunchedEffect(uiState.isConnected) {
        if (!uiState.isConnected && uiState.serverUrl.isEmpty()) {
            onDisconnected()
        }
    }

    LaunchedEffect(uiState.exportMessage) {
        uiState.exportMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearExportMessage()
        }
    }

    // Disconnect confirmation dialog
    if (uiState.showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideDisconnectDialog,
            title = { Text(stringResource(R.string.settings_disconnect)) },
            text = { Text("Deseja realmente desconectar este dispositivo? Você precisará configurar novamente.") },
            confirmButton = {
                TextButton(onClick = viewModel::disconnect) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideDisconnectDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Clear data confirmation dialog
    if (uiState.showClearDataDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideClearDataDialog,
            title = { Text("Limpar Dados") },
            text = { Text("Isso irá excluir todas as notificações capturadas localmente. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = viewModel::clearAllData) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideClearDataDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add app dialog
    if (uiState.showAddAppDialog) {
        AddAppDialog(
            installedApps = uiState.installedApps,
            searchQuery = uiState.appSearchQuery,
            isLoading = uiState.isLoadingApps,
            onSearchQueryChange = viewModel::updateAppSearchQuery,
            onAppSelected = { app -> viewModel.addApp(app.packageName, app.displayName) },
            onDismiss = viewModel::hideAddAppDialog
        )
    }

    // Edit server dialog
    if (uiState.showEditServerDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideEditServerDialog,
            title = { Text("Editar Servidor") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.editServerUrl,
                        onValueChange = viewModel::updateEditServerUrl,
                        label = { Text("URL do Servidor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.editToken,
                        onValueChange = viewModel::updateEditToken,
                        label = { Text("Token de Acesso") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveServerSettings) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideEditServerDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Section
            item {
                SectionHeader(title = stringResource(R.string.settings_server))
            }

            item {
                ServerCard(
                    serverUrl = uiState.serverUrl,
                    tokenName = uiState.tokenName,
                    isConnected = uiState.isConnected,
                    onEdit = viewModel::showEditServerDialog,
                    onDisconnect = viewModel::showDisconnectDialog
                )
            }

            uiState.tokenExpiresInDays?.let { daysLeft ->
                if (daysLeft <= 30) {
                    item {
                        TokenExpiryWarningCard(
                            daysLeft = daysLeft,
                            onEdit = viewModel::showEditServerDialog
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Bateria")
            }

            item {
                BatteryOptimizationStatusItem(
                    isExempt = uiState.hasBatteryOptimizationExemption,
                    onManage = {
                        val intent = if (uiState.hasBatteryOptimizationExemption) {
                            viewModel.openBatteryOptimizationSettings()
                        } else {
                            viewModel.requestIgnoreBatteryOptimizationsIntent()
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(viewModel.openBatteryOptimizationSettings())
                        }
                    }
                )
            }

            item {
                SectionHeader(title = "Alertas do Companion")
            }

            item {
                NotificationPreferenceItem(
                    title = "Confirmar envio com notificação",
                    subtitle = "Avisa no telefone quando um lançamento for enviado com sucesso",
                    checked = uiState.notifySyncSuccess,
                    onCheckedChange = { enabled ->
                        updateAlertPreference(enabled, viewModel::setNotifySyncSuccess)
                    }
                )
            }

            item {
                NotificationPreferenceItem(
                    title = "Avisar erro de envio",
                    subtitle = "Mostra uma notificação quando houver falha ao enviar um lançamento",
                    checked = uiState.notifySyncError,
                    onCheckedChange = { enabled ->
                        updateAlertPreference(enabled, viewModel::setNotifySyncError)
                    }
                )
            }

            // Monitored Apps Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(title = stringResource(R.string.settings_monitored_apps))
                    OutlinedButton(
                        onClick = viewModel::showAddAppDialog
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar")
                    }
                }
            }

            if (uiState.monitoredApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "Nenhum app configurado. Toque em \"Adicionar\" para selecionar apps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(uiState.monitoredApps) { app ->
                AppToggleItem(
                    app = app,
                    onToggle = { enabled -> viewModel.toggleApp(app.packageName, enabled) },
                    onRemove = { viewModel.removeApp(app.packageName) }
                )
            }

            // Data Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Dados")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    onClick = viewModel::exportNotifications,
                    enabled = !uiState.isExportingNotifications
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.isExportingNotifications) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exportar Notificações",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (uiState.isExportingNotifications) {
                                    "Gerando arquivo JSON em Downloads..."
                                } else {
                                    "Salvar notificações capturadas em um arquivo JSON"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    onClick = viewModel::showClearDataDialog
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Limpar Notificações Locais",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Excluir todas as notificações capturadas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // About Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = stringResource(R.string.settings_about))
            }

            item {
                AboutCard(
                    appVersion = uiState.appVersion,
                    onOpenCompanion = {
                        uriHandler.openUri("https://github.com/felipegcoutinho/openmonetis-companion")
                    },
                    onOpenOpenMonetis = {
                        uriHandler.openUri("https://github.com/felipegcoutinho/openmonetis")
                    },
                    onOpenAuthor = {
                        uriHandler.openUri("https://github.com/felipegcoutinho")
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutCard(
    appVersion: String,
    onOpenCompanion: () -> Unit,
    onOpenOpenMonetis: () -> Unit,
    onOpenAuthor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            AboutRow(
                title = stringResource(R.string.settings_version),
                value = appVersion
            )
            HorizontalDivider()
            AboutRow(
                title = "Código-fonte do Companion",
                value = "felipegcoutinho/openmonetis-companion",
                onClick = onOpenCompanion
            )
            HorizontalDivider()
            AboutRow(
                title = "Projeto principal",
                value = "felipegcoutinho/openmonetis",
                onClick = onOpenOpenMonetis
            )
            HorizontalDivider()
            AboutRow(
                title = "Desenvolvido por",
                value = "felipegcoutinho",
                onClick = onOpenAuthor
            )
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Abrir $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NotificationPreferenceItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun AddAppDialog(
    installedApps: List<InstalledAppUi>,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledAppUi) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adicionar App")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar app...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    installedApps.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "Nenhum app encontrado" else "Nenhum app disponível",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(installedApps) { app ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    onClick = { onAppSelected(app) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        app.icon?.let { icon ->
                                            Image(
                                                painter = rememberDrawablePainter(drawable = icon),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ServerCard(
    serverUrl: String,
    tokenName: String,
    isConnected: Boolean,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = if (isConnected) {
                            stringResource(R.string.settings_server_connected)
                        } else {
                            stringResource(R.string.settings_server_disconnected)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isConnected) {
                        IconButton(onClick = onDisconnect) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Desconectar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.settings_server_url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = serverUrl.ifEmpty { "-" },
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_token_name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tokenName.ifEmpty { "-" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TokenExpiryWarningCard(
    daysLeft: Long,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (daysLeft <= 0) {
                        "Seu token de acesso expirou"
                    } else {
                        "Seu token de acesso expira em $daysLeft dia${if (daysLeft == 1L) "" else "s"}"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Gere um novo token em Ajustes > Companion no OpenMonetis e atualize aqui, ou a sincronização vai parar de funcionar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationStatusItem(
    isExempt: Boolean,
    onManage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onManage
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isExempt) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (isExempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Otimização de bateria",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (isExempt) {
                        "Desativada — a captura em segundo plano está protegida. Toque para gerenciar."
                    } else {
                        "Ativada — o sistema pode interromper a captura em segundo plano. Toque para desativar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Gerenciar",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AppToggleItem(
    app: MonitoredAppUi,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            app.icon?.let { icon ->
                Image(
                    painter = rememberDrawablePainter(drawable = icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = app.isEnabled,
                    onCheckedChange = onToggle
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remover",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

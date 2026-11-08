package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.ui.theme.AppColors

/** What the chart screen needs to show and manage named chart layouts. */
data class LayoutControls(
    val name: String,
    val names: List<String>,
    /** True when the chart differs from the saved layout, so "Save" is offered. */
    val dirty: Boolean,
    val onSave: () -> Unit,
    /** Returns false when the name is empty or already used. */
    val onCopy: (String) -> Boolean,
    val onRename: (String) -> Boolean,
    val onOpen: (String) -> Unit,
)

private enum class LayoutDialog { COPY, RENAME, OPEN }

/** Dims the chart area and shows the layout actions panel at its bottom. */
@Composable
fun LayoutMenuOverlay(layout: LayoutControls, onDismiss: () -> Unit, onResult: (OrderResult) -> Unit) {
    var dialog by remember { mutableStateOf<LayoutDialog?>(null) }
    androidx.activity.compose.BackHandler(enabled = dialog == null, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(AppColors.SheetCard)
                .padding(vertical = 6.dp),
        ) {
            MenuItem("Save layout", enabled = layout.dirty) {
                layout.onSave()
                onDismiss()
            }
            MenuItem("Make a copy…") { dialog = LayoutDialog.COPY }
            MenuItem("Rename…") { dialog = LayoutDialog.RENAME }
            HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { dialog = LayoutDialog.OPEN }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Open layout…", fontSize = 15.sp)
            }
        }
    }

    when (dialog) {
        LayoutDialog.COPY -> NameDialog("Make a copy", "${layout.name} copy", onDismiss = { dialog = null }) { name ->
            layout.onCopy(name).also { ok ->
                if (ok) {
                    dialog = null
                    onDismiss()
                    onResult(OrderResult.Success("Layout \"$name\" created"))
                }
            }
        }
        LayoutDialog.RENAME -> NameDialog("Rename layout", layout.name, onDismiss = { dialog = null }) { name ->
            layout.onRename(name).also { ok ->
                if (ok) {
                    dialog = null
                    onDismiss()
                }
            }
        }
        LayoutDialog.OPEN -> OpenLayoutDialog(layout.names, layout.name, onDismiss = { dialog = null }) { name ->
            layout.onOpen(name)
            dialog = null
            onDismiss()
        }
        null -> Unit
    }
}

@Composable
private fun MenuItem(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Boolean) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SheetCard,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(30)
                    error = false
                },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text("This name is empty or already used") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent),
            )
        },
        confirmButton = {
            TextButton(onClick = { error = !onConfirm(name.trim()) }) { Text("OK", color = AppColors.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) } },
    )
}

@Composable
private fun OpenLayoutDialog(names: List<String>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SheetCard,
        title = { Text("Open layout", fontSize = 17.sp) },
        text = {
            Column {
                names.forEach { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(n) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = n == current,
                            onClick = { onPick(n) },
                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent),
                        )
                        Text(n, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = AppColors.TextSecondary) } },
    )
}

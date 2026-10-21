package notesplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import notesplus.domain.Accent
import notesplus.domain.ThemeMode

@Composable
fun SettingsScreen(settings: SettingsState, backup: BackupState) {
    val s = settings.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Strings.SETTINGS, style = MaterialTheme.typography.headlineSmall)

        Section(Strings.APPEARANCE)
        Text(Strings.THEME, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                ThemeMode.LIGHT to Strings.THEME_LIGHT,
                ThemeMode.DARK to Strings.THEME_DARK,
                ThemeMode.SYSTEM to Strings.THEME_SYSTEM,
            ).forEach { (mode, label) ->
                Row(
                    Modifier.selectable(selected = s.theme == mode, onClick = { settings.update { it.copy(theme = mode) } }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.theme == mode, onClick = null)
                    Text(label, Modifier.padding(start = 4.dp))
                }
            }
        }

        Text(Strings.ACCENT_COLOR, fontWeight = FontWeight.SemiBold)
        FilterChip(
            selected = s.accent == Accent.AUTO,
            onClick = { settings.update { it.copy(accent = Accent.AUTO) } },
            label = { Text(Strings.ACCENT_AUTO) },
        )
        ACCENT_PALETTE.chunked(8).forEachIndexed { row, colors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEachIndexed { col, color ->
                    val index = row * 8 + col
                    val selected = s.accent.paletteIndex == index
                    Box(
                        Modifier.size(32.dp)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .padding(if (selected) 5.dp else 0.dp)
                            .background(color, CircleShape)
                            .clickable { settings.update { it.copy(accent = Accent(index)) } },
                    )
                }
            }
        }

        Section(Strings.LANGUAGE)
        LanguagePicker(settings)
        Text(Strings.LANGUAGE_HINT, style = MaterialTheme.typography.bodySmall)

        Section(Strings.EDITOR)
        SwitchRow(Strings.AUTOSAVE, s.autosave) { v -> settings.update { it.copy(autosave = v) } }
        Text(Strings.AUTOSAVE_HINT, style = MaterialTheme.typography.bodySmall)

        Section(Strings.STARTUP)
        val canAutostart = settings.autostartAvailable
        SwitchRow(Strings.START_WITH_WINDOWS, s.startWithWindows, enabled = canAutostart) { v ->
            settings.update { it.copy(startWithWindows = v) }
        }
        SwitchRow(Strings.BACKGROUND_AUTOSTART, s.backgroundAutostart, enabled = canAutostart && s.startWithWindows) { v ->
            settings.update { it.copy(backgroundAutostart = v) }
        }
        if (!canAutostart) Text(Strings.AUTOSTART_UNAVAILABLE, style = MaterialTheme.typography.bodySmall)

        Section(Strings.BACKUP)
        BackupSection(backup)
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun LanguagePicker(settings: SettingsState) {
    var open by remember { mutableStateOf(false) }
    val override = settings.settings.languageOverride
    val label = override?.let { Strings.languageName(it) } ?: Strings.LANGUAGE_SYSTEM
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Strings.LANGUAGE_SYSTEM) }, onClick = {
                open = false
                settings.update { it.copy(languageOverride = null) }
            })
            settings.languageChoices.forEach { lang ->
                DropdownMenuItem(text = { Text(Strings.languageName(lang)) }, onClick = {
                    open = false
                    settings.update { it.copy(languageOverride = lang) }
                })
            }
        }
    }
}

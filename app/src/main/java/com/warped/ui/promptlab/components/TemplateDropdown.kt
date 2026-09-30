package com.warped.ui.promptlab.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.warped.R
import com.warped.domain.prompt.PromptTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDropdown(
    templates: List<PromptTemplate>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = templates.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let { templateDisplayName(it) } ?: stringResource(R.string.lab_select_template),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.lab_template)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            templates.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = templateDisplayName(t),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        onSelect(t.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

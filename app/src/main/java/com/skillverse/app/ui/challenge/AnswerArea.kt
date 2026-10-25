package com.skillverse.app.ui.challenge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skillverse.app.core.designsystem.CodeTextStyle
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType

/**
 * Type-specific answer input shared by the challenge and boss screens. When [reviewMode] is true
 * (after submission), choices are coloured to show what was selected; the correct option is
 * highlighted only if [showSolution] is true.
 */
@Composable
fun AnswerArea(
    challenge: Challenge,
    input: AnswerInput,
    onInput: (AnswerInput) -> Unit,
    enabled: Boolean,
    reviewMode: Boolean,
    showSolution: Boolean,
    lastAnswerCorrect: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (challenge.type) {
        ChallengeType.MULTIPLE_CHOICE, ChallengeType.IDENTIFY_BUG -> ChoiceList(
            challenge, input.choice, { onInput(input.copy(choice = it)) }, enabled, reviewMode, showSolution, lastAnswerCorrect, modifier,
        )
        ChallengeType.ARRANGE_STEPS -> ArrangeList(input.order, { onInput(input.copy(order = it)) }, enabled, modifier)
        ChallengeType.PREDICT_OUTPUT, ChallengeType.FILL_BLANK, ChallengeType.CODE_PUZZLE -> CodeAnswerField(
            challenge, input.text, { onInput(input.copy(text = it)) }, enabled, modifier,
        )
    }
}

@Composable
private fun ChoiceList(
    challenge: Challenge,
    selected: Int?,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    reviewMode: Boolean,
    showSolution: Boolean,
    lastAnswerCorrect: Boolean?,
    modifier: Modifier,
) {
    val correctIndex = (challenge.answer as? AnswerSpec.Choice)?.correctIndex
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        challenge.options.forEachIndexed { index, option ->
            val isSelected = selected == index
            val isCorrect = index == correctIndex
            val accent: Color = when {
                reviewMode && showSolution && isCorrect -> SvTheme.colors.success
                reviewMode && isSelected && lastAnswerCorrect == true -> SvTheme.colors.success
                reviewMode && isSelected && lastAnswerCorrect == false -> SvTheme.colors.danger
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            val letter = ('A' + index).toString()
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected || (reviewMode && showSolution && isCorrect)) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                    .testTag("option_$index"),
            ) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = if (isSelected) 1f else 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(letter, style = MaterialTheme.typography.labelMedium, color = if (isSelected) Color.White else accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        option,
                        style = if (looksLikeCode(option)) CodeTextStyle else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun looksLikeCode(s: String): Boolean =
    s.contains('(') && s.contains(')') && (s.contains('.') || s.contains('"') || s.contains('=')) && !s.contains(", so ")

@Composable
private fun ArrangeList(order: List<String>, onOrder: (List<String>) -> Unit, enabled: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Use the arrows to move each step. The first item runs first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        order.forEachIndexed { index, step ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(start = Spacing.md)
                    .semantics(mergeDescendants = true) { contentDescription = "Position ${index + 1}: $step" }
                    .testTag("step_$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                Text(
                    step,
                    style = if (step.startsWith(" ") || step.contains("(") && step.contains(")")) CodeTextStyle else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(vertical = Spacing.md),
                )
                Column {
                    IconButton(
                        onClick = { onOrder(order.toMutableList().apply { add(index - 1, removeAt(index)) }) },
                        enabled = enabled && index > 0,
                        modifier = Modifier.size(40.dp).testTag("step_up_$index"),
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                    IconButton(
                        onClick = { onOrder(order.toMutableList().apply { add(index + 1, removeAt(index)) }) },
                        enabled = enabled && index < order.lastIndex,
                        modifier = Modifier.size(40.dp).testTag("step_down_$index"),
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                }
            }
        }
    }
}

@Composable
private fun CodeAnswerField(challenge: Challenge, text: String, onText: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    val (label, single) = when (challenge.type) {
        ChallengeType.PREDICT_OUTPUT -> "Program output" to false
        ChallengeType.FILL_BLANK -> "Text for the blank" to true
        else -> "Your code" to false
    }
    OutlinedTextField(
        value = text,
        onValueChange = { onText(it.take(2000)) },
        enabled = enabled,
        label = { Text(label) },
        textStyle = CodeTextStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
        singleLine = single,
        minLines = if (single) 1 else 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = if (single) ImeAction.Done else ImeAction.Default,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.fillMaxWidth().testTag("answer_field"),
    )
}

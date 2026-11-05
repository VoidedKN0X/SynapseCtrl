package com.vknxworks.synapsectrl.ui.components

import com.vknxworks.synapsectrl.ui.lock.GuardWindow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.ui.lock.LocalOverLock
import com.vknxworks.synapsectrl.ui.lock.findActivity
import com.vknxworks.synapsectrl.ui.lock.lockCovering
import com.vknxworks.synapsectrl.ui.theme.CtrlColors
import com.vknxworks.synapsectrl.ui.theme.CtrlShapes
import com.vknxworks.synapsectrl.ui.theme.CtrlType

// Ctrl / Signal components shared by every screen: cards, the hero card, the floating tab bar,
// the switch, buttons, text fields, dialogs and sheets.

/** The 1dp border of cards. */
val CardBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.Outline)

/** The 1dp border of controls (avatar, square buttons, secondary buttons, the tab bar). */
val ControlBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.ControlOutline)

// ---------- Type helpers ----------

/** "AD/CTRL": the app's wordmark, with the slash in the accent. */
@Composable
fun CtrlWordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name).removeSuffix("Ctrl").uppercase()
    Text(
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** A section label: JetBrains Mono, upper case, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** An 8dp square status dot. */
@Composable
fun CtrlDot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
    Box(modifier.size(size.dp).clip(CtrlShapes.Dot).background(color))
}

// ---------- Cards ----------

/** A secondary card: surface fill, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = CardBorder,
    color: Color = CtrlColors.Surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = CtrlShapes.Card, color = color, contentColor = CtrlColors.Text, border = border, modifier = modifier) {
        Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
}

/** The hero card: the one most important thing on a screen, filled with the accent. */
@Composable
fun CtrlHeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = CtrlShapes.Hero, color = CtrlColors.Accent, contentColor = CtrlColors.OnAccent, modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/**
 * Hides the top divider of the first row in a card: every row draws a 1dp line above itself, and this
 * moves the content up by that line inside the card's clip, so lines only show between rows.
 */
fun Modifier.hideFirstDivider(): Modifier = layout { measurable, constraints ->
    val line = 1.dp.roundToPx()
    val p = measurable.measure(constraints)
    val h = (p.height - line).coerceAtLeast(0)
    layout(p.width, h) { p.place(0, -line) }
}

/** Draws the 1dp list divider above a row. */
@Composable
fun Modifier.topDivider(color: Color = CtrlColors.Outline): Modifier = drawBehind {
    drawRect(color, Offset.Zero, Size(size.width, 1.dp.toPx()))
}

/** A 6dp progress bar. */
@Composable
fun CtrlProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = CtrlColors.TrackFill) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CtrlColors.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

// ---------- Controls ----------

/**
 * The Ctrl switch: a 52x32dp rounded rectangle with a 24dp knob. Accent track and dark knob when on,
 * grey when off. Still a switch for TalkBack.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchOffKnob, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(modifier.minimumInteractiveComponentSize().then(toggle), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .alpha(if (enabled) 1f else 0.4f)
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(track)
                .padding(4.dp),
        ) {
            Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
        }
    }
}

/** The main action: accent fill, dark text, 56dp tall, 18dp corners. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.OnAccent,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Disabled,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A secondary action: surface fill with a 1dp border. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CtrlShapes.Control,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Disabled,
        ),
        border = ControlBorder,
        content = content,
    )
}

/** A text-only action in the accent, with 14dp corners instead of a pill. */
@Composable
fun CtrlTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(14.dp), content = content)
}

/** A 44dp square icon button with the same look as the profile picture (back, actions). */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.Text,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CtrlShapes.Avatar,
        color = CtrlColors.Surface,
        contentColor = tint,
        border = ControlBorder,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(20.dp).alpha(if (enabled) 1f else 0.4f))
        }
    }
}

/** A filter chip: 10dp corners, accent fill when selected. */
@Composable
fun CtrlFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CtrlColors.Surface,
            labelColor = CtrlColors.Text,
            selectedContainerColor = CtrlColors.Accent,
            selectedLabelColor = CtrlColors.OnAccent,
            selectedLeadingIconColor = CtrlColors.OnAccent,
            selectedTrailingIconColor = CtrlColors.OnAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CtrlColors.ControlOutline,
            selectedBorderColor = CtrlColors.Accent,
        ),
    )
}

/** A text field: surface fill, 1dp border, 16dp corners, accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        shape = CtrlShapes.Control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            errorContainerColor = CtrlColors.Surface,
            focusedBorderColor = CtrlColors.AccentText,
            unfocusedBorderColor = CtrlColors.ControlOutline,
            disabledBorderColor = CtrlColors.Outline,
            cursorColor = CtrlColors.AccentText,
            focusedLabelColor = CtrlColors.AccentText,
            unfocusedLabelColor = CtrlColors.Muted,
            focusedLeadingIconColor = CtrlColors.Muted,
            unfocusedLeadingIconColor = CtrlColors.Muted,
            focusedTrailingIconColor = CtrlColors.Muted,
            unfocusedTrailingIconColor = CtrlColors.Muted,
            focusedPlaceholderColor = CtrlColors.Muted,
            unfocusedPlaceholderColor = CtrlColors.Muted,
        ),
    )
}

// ---------- Dialogs and sheets ----------

/**
 * Whether a dialog or sheet (each its own window) may be shown now. Not while the lock, PIN or "Confirm
 * changes" screen covers the app: a window would sit on top of it, readable and usable. It comes back
 * as it was once the app is unlocked. And never on an activity that is finishing or gone (the app being
 * closed or swiped away), where Android refuses a new window and the app would crash.
 */
@Composable
private fun windowAllowed(): Boolean {
    val context = LocalContext.current
    if (!LocalOverLock.current && lockCovering(context.container.vault)) return false
    val activity = context.findActivity() ?: return true
    return !activity.isFinishing && !activity.isDestroyed
}

/**
 * A dialog: surface fill, 26dp corners, 1dp border. Same slots as Material's AlertDialog. A dialog is
 * its own window, so with [secure] (a password or token is shown) it's kept out of screenshots, screen
 * recordings and Recents itself, whatever "Hide in screenshots" says.
 */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    secure: Boolean = false,
    // Protects a destructive or security confirmation from other apps' overlays while it shows.
    guard: Boolean = false,
) {
    if (!windowAllowed()) return
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            GuardWindow(guard)
            confirmButton()
        },
        modifier = modifier.border(CardBorder, CtrlShapes.Hero),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Hero,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = if (secure) DialogProperties(securePolicy = SecureFlagPolicy.SecureOn) else DialogProperties(),
    )
}

/**
 * A bottom sheet: surface fill, 26dp top corners, no outline (the scrim separates it). No border modifier:
 * ModalBottomSheet applies `modifier` before its own offset/drag, so a border would be drawn in the wrong place.
 */
@Composable
fun CtrlSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!windowAllowed()) return
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = shape,
        containerColor = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        content = content,
    )
}

// ---------- Bottom navigation ----------

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }

data class TabItem(val label: String, val icon: ImageVector)

/**
 * The floating tab bar: 64dp tall, 22dp corners, surface at 92 % with a 1dp border. The selected tab
 * is an accent block with its icon and name; the others show only their icon.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clip(shape)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(ControlBorder, shape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val select = Modifier.selectable(
                selected = selected,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(i)
                },
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
            )
            if (selected) {
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(CtrlShapes.Control)
                        .background(CtrlColors.Accent)
                        .then(select)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, null, Modifier.size(18.dp), tint = CtrlColors.OnAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CtrlColors.OnAccent, maxLines = 1)
                }
            } else {
                Box(Modifier.size(48.dp).clip(CtrlShapes.Control).then(select), contentAlignment = Alignment.Center) {
                    Icon(item.icon, item.label, Modifier.size(20.dp), tint = CtrlColors.Muted)
                }
            }
        }
    }
}

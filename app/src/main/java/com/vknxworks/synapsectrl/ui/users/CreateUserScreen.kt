package com.vknxworks.synapsectrl.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.admin.createUser
import com.vknxworks.synapsectrl.data.admin.user
import com.vknxworks.synapsectrl.data.api.ApiError
import com.vknxworks.synapsectrl.ui.common.fullUserId
import com.vknxworks.synapsectrl.ui.common.rememberScreenActions
import com.vknxworks.synapsectrl.ui.common.strongPassword
import com.vknxworks.synapsectrl.ui.components.CtrlTextButton
import com.vknxworks.synapsectrl.ui.components.CtrlTextField
import com.vknxworks.synapsectrl.ui.components.Group
import com.vknxworks.synapsectrl.ui.components.GroupSwitch
import com.vknxworks.synapsectrl.ui.components.PillButton
import com.vknxworks.synapsectrl.ui.components.ScreenTopBar
import com.vknxworks.synapsectrl.ui.components.SecureScreen
import com.vknxworks.synapsectrl.ui.components.copySensitive
import com.vknxworks.synapsectrl.ui.nav.LocalNavigator
import com.vknxworks.synapsectrl.ui.nav.UserRoute
import com.vknxworks.synapsectrl.ui.nav.CreateUserRoute
import com.vknxworks.synapsectrl.ui.theme.CtrlType
import com.vknxworks.synapsectrl.ui.theme.Gutter

private val LOCALPART = Regex("^[a-z0-9._=\\-/+]+$")

/**
 * Creates a local account with PUT /_synapse/admin/v2/users/<user_id>. Synapse would change an
 * existing account through the same call, so SynapseCtrl first checks that the user ID is free.
 */
@Composable
fun CreateUserScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val serverName = active?.server?.serverName.orEmpty()
    val actions = rememberScreenActions()

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(strongPassword()) }
    var admin by remember { mutableStateOf(false) }

    val localpart = username.trim().removePrefix("@").substringBefore(':').lowercase()
    val validName = localpart.isNotEmpty() && LOCALPART.matches(localpart)
    val userId = if (validName) fullUserId(localpart, serverName) else ""
    val canCreate = validName && password.length >= 8 && !actions.busy && active?.server?.readOnly == false

    SecureScreen()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.create_user))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CtrlTextField(
                        value = username,
                        onValueChange = { username = it.trim() },
                        label = { Text(stringResource(R.string.field_new_username)) },
                        supportingText = {
                            Text(
                                when {
                                    username.isBlank() -> stringResource(R.string.username_rules)
                                    !validName -> stringResource(R.string.username_invalid)
                                    else -> stringResource(R.string.username_will_be, userId)
                                },
                            )
                        },
                        isError = username.isNotBlank() && !validName,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.display_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) },
                        supportingText = { Text(stringResource(R.string.password_min)) },
                        isError = password.isNotEmpty() && password.length < 8,
                        singleLine = true,
                        textStyle = CtrlType.Data,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CtrlTextButton(onClick = { password = strongPassword() }) { Text(stringResource(R.string.generate)) }
                        CtrlTextButton(onClick = {
                            copySensitive(context, userId, password)
                            actions.say(context.getString(R.string.copied))
                        }) { Text(stringResource(R.string.copy)) }
                    }
                }
                Group(footer = stringResource(R.string.create_admin_footer)) {
                    GroupSwitch(stringResource(R.string.server_admin), stringResource(R.string.server_admin_subtitle), checked = admin, icon = Icons.Outlined.AdminPanelSettings) { admin = it }
                }
                Text(stringResource(R.string.create_user_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter))
                PillButton(
                    text = stringResource(R.string.create_user),
                    enabled = canCreate,
                    onClick = {
                        val client = active?.client ?: return@PillButton
                        actions.run(null) {
                            // The same call would change an existing account: make sure there isn't one.
                            val exists = try {
                                client.user(userId)
                                true
                            } catch (e: ApiError.NotFound) {
                                false
                            }
                            if (exists) {
                                actions.say(context.getString(R.string.user_exists, userId))
                                return@run
                            }
                            client.createUser(userId, password, displayName.trim().ifBlank { null }, admin)
                            // Replace this screen with the new user's page.
                            nav.nav.navigate(UserRoute(userId)) { popUpTo<CreateUserRoute> { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                )
            }
        }
        SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

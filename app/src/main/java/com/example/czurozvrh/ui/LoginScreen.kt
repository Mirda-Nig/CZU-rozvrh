package com.example.czurozvrh.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.czurozvrh.R

@Composable
fun LoginScreen(state: UiState, vm: RozvrhViewModel) {
    var login by rememberSaveable { mutableStateOf(state.savedLogin) }
    var password by rememberSaveable { mutableStateOf("") }
    var studentId by rememberSaveable { mutableStateOf(state.studentId) }
    var code by rememberSaveable { mutableStateOf("") }
    val twoFa = state.pending2FA != null

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Image(
                painterResource(R.drawable.czu_logo), contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text("Rozvrh ČZU", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (twoFa) "Zadej ověřovací kód z UIS." else "Přihlas se jednou údaji do UIS.\nPak už se aplikace přihlašuje sama.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            Column(Modifier.fillMaxWidth().widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val shape = RoundedCornerShape(14.dp)
                val colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
                val mod = Modifier.fillMaxWidth()
                if (!twoFa) {
                    OutlinedTextField(
                        value = login, onValueChange = { login = it }, label = { Text("Uživatelské jméno") },
                        singleLine = true, modifier = mod, shape = shape, colors = colors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Heslo") },
                        singleLine = true, modifier = mod, shape = shape, colors = colors,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = studentId, onValueChange = { studentId = it.filter(Char::isDigit) },
                        label = { Text("Číslo rozvrhu (nepovinné)") },
                        supportingText = { Text("Najde se samo. Ručně: číslo za rozvrh_student= v adrese rozvrhu.") },
                        singleLine = true, modifier = mod, shape = shape, colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it }, label = { Text("Ověřovací kód") },
                        singleLine = true, modifier = mod, shape = shape, colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                state.loginError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.login(login, password, studentId, if (twoFa) code else null) },
                    enabled = !state.loginBusy && login.isNotBlank() && password.isNotEmpty(),
                    modifier = mod.height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.loginBusy) CircularProgressIndicator(
                        Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                    )
                    else Text(if (twoFa) "Ověřit" else "Přihlásit se", style = MaterialTheme.typography.labelLarge)
                }
                if (twoFa) TextButton(onClick = vm::cancel2FA, modifier = mod) { Text("Zpět") }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Heslo se ukládá šifrovaně jen v tomto telefonu.",
                    modifier = mod,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

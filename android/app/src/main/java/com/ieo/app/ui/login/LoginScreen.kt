package com.ieo.app.ui.login

import android.os.SystemClock
import com.ieo.app.ui.component.IeoWordmark
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.R
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoSymbol
import com.ieo.app.ui.theme.IeoTheme

private val PHONE_PATTERN = Regex("^01\\d{8,9}$")
private const val CODE_LENGTH = 6

/** 인증번호 유효 시간. TODO: 서버 인증 구현 시 서버의 만료 시간과 맞춘다 */
private const val CODE_VALID_SECONDS = 180
private const val CODE_WARNING_SECONDS = 30
private val WIDE_LAYOUT_MIN_WIDTH = 840.dp

/**
 * 로그인 화면. 넓은 화면(차량)에서는 왼쪽 브랜드·오른쪽 로그인 두 칸, 좁은 화면에서는 한 칸으로 보여 준다.
 * 실제 인증 처리는 콜백으로 넘긴다.
 */
@Composable
fun LoginScreen(
    onRequestCode: (phone: String) -> Unit,
    onLogin: (phone: String, code: String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
            // 차량 화면처럼 넓을 때: 왼쪽 브랜드, 오른쪽 로그인
            Row(modifier = Modifier.fillMaxSize()) {
                BrandPanel(modifier = Modifier.weight(0.45f).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.weight(0.55f).fillMaxHeight(),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        LoginForm(
                            showSymbol = false,
                            onRequestCode = onRequestCode,
                            onLogin = onLogin,
                            modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 64.dp, vertical = 48.dp),
                        )
                    }
                }
            }
        } else {
            // 좁은 화면: 한 줄로 쌓기
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LoginForm(
                        showSymbol = true,
                        onRequestCode = onRequestCode,
                        onLogin = onLogin,
                        modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 48.dp, vertical = 40.dp),
                    )
                }
            }
        }
    }
}

/** 넓은 화면 왼쪽: 로고와 문구. 배경은 오른쪽 폼과 같은 색이고, 가운데 가는 세로선으로만 나눈다 */
@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IeoWordmark(width = 200.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
    }
}

private enum class LoginTab(val label: Int) {
    QR(R.string.login_tab_qr),
    PHONE(R.string.login_tab_phone),
}

/**
 * 로그인 폼: QR 로그인(기본)과 휴대폰 번호 로그인 두 탭.
 * (Google 로그인은 차량에 Google 서비스가 없어 모바일 앱에서만 제공한다)
 * 휴대폰 인증번호는 받은 뒤 [CODE_VALID_SECONDS]초 동안만 쓸 수 있고, 남은 시간을 보여 준다.
 */
@Composable
private fun LoginForm(
    showSymbol: Boolean,
    onRequestCode: (phone: String) -> Unit,
    onLogin: (phone: String, code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(LoginTab.QR) }
    // 휴대폰 탭 입력값은 탭을 오가도 남도록 폼에서 들고 있는다
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    // 인증번호 만료 시각 (SystemClock.elapsedRealtime 기준). 0이면 아직 받지 않음
    var codeExpiresAt by rememberSaveable { mutableLongStateOf(0L) }
    val codeSent = codeExpiresAt > 0L
    val phoneValid = PHONE_PATTERN.matches(phone)

    var secondsLeft by remember(codeExpiresAt) { mutableIntStateOf(secondsUntil(codeExpiresAt)) }
    LaunchedEffect(codeExpiresAt) {
        while (codeExpiresAt > 0L && secondsLeft > 0) {
            delay(250)
            secondsLeft = secondsUntil(codeExpiresAt)
        }
    }
    val codeExpired = codeSent && secondsLeft == 0

    val requestCode = {
        code = ""
        codeExpiresAt = SystemClock.elapsedRealtime() + CODE_VALID_SECONDS * 1000L
        onRequestCode(phone)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSymbol) {
                IeoSymbol(size = 56.dp)
                Spacer(Modifier.width(20.dp))
            }
            Column {
                Text(text = stringResource(R.string.login_title), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(if (tab == LoginTab.QR) R.string.login_subtitle_qr else R.string.login_subtitle),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            LoginTab.entries.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text(stringResource(item.label), fontSize = 22.sp, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.heightIn(min = 64.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        if (tab == LoginTab.QR) {
            QrLoginContent(modifier = Modifier.fillMaxWidth())
            return@Column
        }

        // 휴대폰 번호: 숫자만 저장하고 화면에는 010-1234-5678처럼 하이픈을 넣어 보여 준다
        OutlinedTextField(
            value = phone,
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(11)
                if (digits != phone) {
                    phone = digits
                    // 번호를 바꾸면 이전에 받은 인증번호는 쓸 수 없다
                    codeExpiresAt = 0L
                    code = ""
                }
            },
            label = { Text(stringResource(R.string.login_phone_label), fontSize = 18.sp) },
            placeholder = { Text(stringResource(R.string.login_phone_placeholder), fontSize = 24.sp) },
            visualTransformation = PhoneNumberVisualTransformation(),
            textStyle = TextStyle(fontSize = 24.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (!codeSent) {
            IeoPrimaryButton(
                text = stringResource(R.string.login_request_code),
                enabled = phoneValid,
                onClick = requestCode,
            )
        } else {
            Text(
                text = stringResource(R.string.login_code_sent),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.filter(Char::isDigit).take(CODE_LENGTH) },
                label = { Text(stringResource(R.string.login_code_label), fontSize = 18.sp) },
                trailingIcon = { CodeTimer(secondsLeft) },
                textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp),
                singleLine = true,
                enabled = !codeExpired,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (codeExpired) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_code_expired),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            IeoPrimaryButton(
                text = stringResource(R.string.login_submit),
                enabled = phoneValid && code.length == CODE_LENGTH && !codeExpired,
                onClick = { onLogin(phone, code) },
            )
            TextButton(
                onClick = requestCode,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.login_resend_code), fontSize = 20.sp)
            }
        }
    }
}

/** 인증번호 남은 시간 (m:ss). 30초 이하로 남으면 경고색으로 바뀐다 */
@Composable
private fun CodeTimer(secondsLeft: Int) {
    Text(
        text = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        color = if (secondsLeft <= CODE_WARNING_SECONDS) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(end = 16.dp),
    )
}

internal fun secondsUntil(expiresAt: Long): Int {
    if (expiresAt == 0L) return 0
    val millisLeft = expiresAt - SystemClock.elapsedRealtime()
    return ((millisLeft + 999) / 1000).toInt().coerceAtLeast(0)
}

@Preview(widthDp = 1280, heightDp = 900)
@Composable
private fun LoginScreenPreview() {
    IeoTheme(darkTheme = false) {
        LoginScreen(onRequestCode = {}, onLogin = { _, _ -> })
    }
}

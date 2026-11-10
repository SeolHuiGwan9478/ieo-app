package com.ieo.app.ui.login

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.R
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoSymbol
import com.ieo.app.ui.theme.IeoTheme

private const val CODE_WARNING_SECONDS = 30
private val WIDE_LAYOUT_MIN_WIDTH = 840.dp

/**
 * 로그인 화면. 넓은 화면(차량)에서는 왼쪽 브랜드·오른쪽 로그인 두 칸, 좁은 화면에서는 한 칸으로 보여 준다.
 * 상태는 받기만 하고(휴대폰: [PhoneLoginViewModel], QR: [QrLoginViewModel]), 사용자 동작은 콜백으로 넘긴다.
 * ViewModel과 연결하는 곳은 [LoginRoute].
 */
@Composable
fun LoginScreen(
    phone: PhoneLoginUiState,
    phoneActions: PhoneLoginActions,
    qr: QrLoginUiState,
    qrActions: QrLoginActions,
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
                            phone = phone,
                            phoneActions = phoneActions,
                            qr = qr,
                            qrActions = qrActions,
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
                        phone = phone,
                        phoneActions = phoneActions,
                        qr = qr,
                        qrActions = qrActions,
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
 * 휴대폰 인증번호는 받은 뒤 3분 동안만 쓸 수 있고, 남은 시간을 보여 준다.
 */
@Composable
private fun LoginForm(
    showSymbol: Boolean,
    phone: PhoneLoginUiState,
    phoneActions: PhoneLoginActions,
    qr: QrLoginUiState,
    qrActions: QrLoginActions,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(LoginTab.QR) }

    var secondsLeft by remember(phone.codeExpiresAt) { mutableIntStateOf(secondsUntil(phone.codeExpiresAt)) }
    LaunchedEffect(phone.codeExpiresAt) {
        while (phone.codeSent && secondsLeft > 0) {
            delay(250)
            secondsLeft = secondsUntil(phone.codeExpiresAt)
        }
    }
    val codeExpired = phone.codeSent && secondsLeft == 0

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
                    fontSize = 24.sp,
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
                    text = { Text(stringResource(item.label), fontSize = 24.sp, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.heightIn(min = 76.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        if (tab == LoginTab.QR) {
            // QR 탭이 보일 때만 서버에 승인 여부를 묻는다
            DisposableEffect(Unit) {
                qrActions.onVisible()
                onDispose { qrActions.onHidden() }
            }
            QrLoginContent(state = qr, onRefresh = qrActions::refresh, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        // 휴대폰 번호: 숫자만 저장하고 화면에는 010-1234-5678처럼 하이픈을 넣어 보여 준다
        OutlinedTextField(
            value = phone.phone,
            onValueChange = phoneActions::onPhoneChange,
            label = { Text(stringResource(R.string.login_phone_label), fontSize = 24.sp) },
            placeholder = { Text(stringResource(R.string.login_phone_placeholder), fontSize = 24.sp) },
            visualTransformation = PhoneNumberVisualTransformation(),
            textStyle = TextStyle(fontSize = 24.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // 오류 문구는 버튼 위에 둔다. 버튼 아래에 두면 화면 키보드에 가려진다 (에뮬레이터 확인)
        if (!phone.codeSent) {
            phone.errorMessage?.let { ErrorText(it, bottomSpace = 12.dp) }
            IeoPrimaryButton(
                text = stringResource(R.string.login_request_code),
                enabled = phone.phoneValid && !phone.isRequestingCode,
                onClick = phoneActions::requestCode,
            )
        } else {
            Text(
                text = stringResource(R.string.login_code_sent),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone.code,
                onValueChange = phoneActions::onCodeChange,
                label = { Text(stringResource(R.string.login_code_label), fontSize = 24.sp) },
                trailingIcon = { CodeTimer(secondsLeft) },
                textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp),
                singleLine = true,
                enabled = !codeExpired,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            phone.demoCode?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_demo_code, it),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (codeExpired) {
                Spacer(Modifier.height(8.dp))
                ErrorText(stringResource(R.string.login_code_expired))
            }
            phone.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                ErrorText(it)
            }
            Spacer(Modifier.height(16.dp))
            IeoPrimaryButton(
                text = stringResource(R.string.login_submit),
                enabled = phone.phoneValid && phone.codeComplete && !codeExpired && !phone.isLoggingIn,
                onClick = phoneActions::login,
            )
            TextButton(
                onClick = phoneActions::requestCode,
                enabled = !phone.isRequestingCode,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.login_resend_code), fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun ErrorText(message: String, bottomSpace: Dp = 0.dp) {
    // 좁은 반 화면에서도 한 줄로 보이게, 넘치면 글자를 줄인다
    BasicText(
        text = message,
        style = TextStyle(color = MaterialTheme.colorScheme.error),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 24.sp),
    )
    if (bottomSpace > 0.dp) Spacer(Modifier.height(bottomSpace))
}

/** 인증번호 남은 시간 (m:ss). 30초 이하로 남으면 경고색으로 바뀐다 */
@Composable
private fun CodeTimer(secondsLeft: Int) {
    Text(
        text = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
        fontSize = 24.sp,
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
    return ((millisLeft + 999) / 1000).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

@Preview(widthDp = 1280, heightDp = 900)
@Composable
private fun LoginScreenPreview() {
    IeoTheme(darkTheme = false) {
        LoginScreen(
            phone = PhoneLoginUiState(),
            phoneActions = PreviewPhoneLoginActions,
            qr = QrLoginUiState.Loading,
            qrActions = PreviewQrLoginActions,
        )
    }
}

private object PreviewPhoneLoginActions : PhoneLoginActions {
    override fun onPhoneChange(input: String) = Unit
    override fun onCodeChange(input: String) = Unit
    override fun requestCode() = Unit
    override fun login() = Unit
    override fun reset() = Unit
}

private object PreviewQrLoginActions : QrLoginActions {
    override fun onVisible() = Unit
    override fun onHidden() = Unit
    override fun refresh() = Unit
}

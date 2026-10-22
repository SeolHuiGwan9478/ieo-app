package com.ieo.app.ui.login

import androidx.compose.foundation.BorderStroke
import com.ieo.app.ui.component.IeoWordmark
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
private val WIDE_LAYOUT_MIN_WIDTH = 840.dp

/**
 * 로그인 화면. 넓은 화면(차량)에서는 왼쪽 브랜드·오른쪽 로그인 두 칸, 좁은 화면에서는 한 칸으로 보여 준다.
 * 실제 인증 처리는 콜백으로 넘긴다.
 */
@Composable
fun LoginScreen(
    onRequestCode: (phone: String) -> Unit,
    onLogin: (phone: String, code: String) -> Unit,
    onGoogleLogin: () -> Unit,
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
                            onGoogleLogin = onGoogleLogin,
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
                        onGoogleLogin = onGoogleLogin,
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

/** 로그인 폼: 위쪽 휴대폰 인증번호 로그인(일반 로그인), 아래쪽 Google 로그인 */
@Composable
private fun LoginForm(
    showSymbol: Boolean,
    onRequestCode: (phone: String) -> Unit,
    onLogin: (phone: String, code: String) -> Unit,
    onGoogleLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    val phoneValid = PHONE_PATTERN.matches(phone)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSymbol) {
                IeoSymbol(size = 56.dp)
                Spacer(Modifier.width(20.dp))
            }
            Column {
                Text(text = stringResource(R.string.login_title), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.login_subtitle),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(40.dp))

        // 일반 로그인: 휴대폰 번호 → 인증번호
        OutlinedTextField(
            value = phone,
            onValueChange = { input -> phone = input.filter(Char::isDigit).take(11) },
            label = { Text(stringResource(R.string.login_phone_label), fontSize = 18.sp) },
            placeholder = { Text(stringResource(R.string.login_phone_placeholder), fontSize = 24.sp) },
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
                onClick = {
                    codeSent = true
                    onRequestCode(phone)
                },
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
                textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            IeoPrimaryButton(
                text = stringResource(R.string.login_submit),
                enabled = phoneValid && code.length == CODE_LENGTH,
                onClick = { onLogin(phone, code) },
            )
            TextButton(
                onClick = {
                    code = ""
                    onRequestCode(phone)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.login_resend_code), fontSize = 20.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
        OrDivider()
        Spacer(Modifier.height(32.dp))

        GoogleLoginButton(onClick = onGoogleLogin)
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.login_or),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** Google 브랜드 가이드에 맞춰 흰 바탕, 회색 테두리, 컬러 G 로고를 쓴다 (다크 모드에서도 동일) */
@Composable
private fun GoogleLoginButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF747775)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(text = stringResource(R.string.login_google), fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 900)
@Composable
private fun LoginScreenPreview() {
    IeoTheme(darkTheme = false) {
        LoginScreen(onRequestCode = {}, onLogin = { _, _ -> }, onGoogleLogin = {})
    }
}

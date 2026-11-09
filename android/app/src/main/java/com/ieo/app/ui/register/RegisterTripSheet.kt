package com.ieo.app.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.component.FilterChip
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private const val TIME_STEP_MINUTES = 30L

/**
 * "다른 앱에서 받은 배차 등록" 창 (TRIP-001). 상하차지·날짜·결제는 눌러서 고르고, 운임과 주선사 이름만 입력한다.
 * [visible]이 false면 닫힌다. 메인 화면 맨 위에 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.RegisterTripSheet(
    visible: Boolean,
    state: RegisterTripUiState,
    actions: RegisterTripActions,
    onDismiss: () -> Unit,
) {
    IeoBottomSheet(item = state.takeIf { visible }, onDismiss = onDismiss) { s ->
        // 창을 열 때마다 비운다 (visible이 바뀌면 새로 만든다)
        var fare by rememberSaveable(visible) { mutableStateOf("") }
        var broker by rememberSaveable(visible) { mutableStateOf("") }

        SheetTitle("다른 앱에서 받은\n배차 등록")
        Spacer(Modifier.height(24.dp))

        PlacePicker("상차지", s.pickupOptions, s.pickup, s.isLoading, actions::selectPickup)
        Spacer(Modifier.height(24.dp))
        PlacePicker("하차지", s.dropoffOptions, s.dropoff, s.isLoading, actions::selectDropoff)
        Spacer(Modifier.height(24.dp))

        FieldLabel("상차 예정")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlannedDay.entries.forEach { FilterChip(it.label, selected = s.day == it, onClick = { actions.selectDay(it) }) }
            if (s.day != PlannedDay.UNKNOWN) {
                Spacer(Modifier.width(16.dp))
                IeoTonalButton(text = "−", onClick = { actions.shiftTime(-TIME_STEP_MINUTES) }, modifier = Modifier.width(72.dp))
                Text(s.time.format(TimeFormat), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
                IeoTonalButton(text = "+", onClick = { actions.shiftTime(TIME_STEP_MINUTES) }, modifier = Modifier.width(72.dp))
            }
        }
        if (s.day == PlannedDay.UNKNOWN) {
            Text("미정이면 도착한 때부터 대기 시간을 세요", fontSize = 24.sp, color = TextSub)
        }
        Spacer(Modifier.height(24.dp))

        FieldLabel("운임")
        OutlinedTextField(
            value = fare,
            onValueChange = { input -> fare = input.filter(Char::isDigit).take(9) },
            suffix = { Text("원", fontSize = 24.sp, color = TextSub) },
            supportingText = fare.toLongOrNull()?.takeIf { it > 0 }?.let { { Text(formatWon(it), fontSize = 24.sp, color = TextSub) } },
            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentType.entries.forEach { FilterChip(it.label, selected = s.paymentType == it, onClick = { actions.selectPayment(it) }) }
        }
        Spacer(Modifier.height(24.dp))

        FieldLabel("주선사 (선택)")
        if (s.brokerOptions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.brokerOptions.forEach { name -> FilterChip(name, selected = broker == name, onClick = { broker = if (broker == name) "" else name }) }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = broker,
            onValueChange = { broker = it.take(50) },
            placeholder = { Text("주선사 이름", fontSize = 24.sp) },
            textStyle = TextStyle(fontSize = 24.sp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(
                text = if (s.isBusy) "등록 중…" else "내 배차에 넣기",
                onClick = { actions.submit(fare.toLongOrNull() ?: 0, broker) },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** 고를 곳을 칩으로 늘어놓고, 고른 곳의 주소와 어디서 온 곳인지 아래에 보여 준다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacePicker(label: String, options: List<PlaceOption>, selected: PlaceOption?, loading: Boolean, onSelect: (String) -> Unit) {
    FieldLabel(label)
    if (options.isEmpty()) {
        Text(
            if (loading) "불러오는 중…" else "고를 곳이 없어요. 내비에서 목적지를 한 번 검색하면 여기 나와요",
            fontSize = 24.sp,
            color = TextSub,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(option.title, selected = selected?.key == option.key, onClick = { onSelect(option.key) }) }
    }
    selected?.let {
        Spacer(Modifier.height(8.dp))
        Text("${it.address} · ${it.source}", fontSize = 24.sp, color = TextSub)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
    Spacer(Modifier.height(10.dp))
}

package com.ieo.app.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.expense.ExpenseCategory
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.component.FilterChip
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

// 장부 탭의 입력 창. 입력 중인 값은 화면이 들고 있고, "저장"을 누를 때 ViewModel에 넘긴다

/** 경비 추가: 분류 + 금액 + 메모(선택). 음성 입력(LEDG-010)은 음성·안전 UX 작업 때 붙인다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ExpenseForm(busy: Boolean, onSubmit: (ExpenseCategory, Long, String?) -> Unit, onCancel: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.FUEL) }
    var amount by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }

    SheetTitle("경비 추가")
    Spacer(Modifier.height(24.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpenseCategory.entries.forEach { FilterChip(it.label, selected = category == it, onClick = { category = it }) }
    }
    Spacer(Modifier.height(20.dp))
    AmountField(amount, onChange = { amount = it })
    Spacer(Modifier.height(12.dp))
    TextField(memo, label = "메모 (선택)", onChange = { memo = it.take(100) })
    FormButtons(busy, onCancel) { onSubmit(category, amount.toLongOrNull() ?: 0, memo) }
}

/** Ieo로 등록하지 않은 운행 추가 (LEDG-004). 운행일은 오늘 */
@Composable
fun ColumnScope.TripForm(busy: Boolean, onSubmit: (String, Long, PaymentType, String?, Int?) -> Unit, onCancel: () -> Unit) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var paymentType by rememberSaveable { mutableStateOf(PaymentType.RECEIPT) }
    var broker by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }

    SheetTitle("오늘 운행 직접 추가")
    Spacer(Modifier.height(24.dp))
    TextField(description, label = "구간 (예: 평택 → 대구)", onChange = { description = it.take(100) })
    Spacer(Modifier.height(12.dp))
    AmountField(amount, label = "운임", onChange = { amount = it })
    Spacer(Modifier.height(16.dp))
    PaymentTypePicker(paymentType, onSelect = { paymentType = it })
    Spacer(Modifier.height(16.dp))
    TextField(broker, label = "주선사 (선택)", onChange = { broker = it.take(50) })
    Spacer(Modifier.height(12.dp))
    DistanceField(distance, onChange = { distance = it })
    FormButtons(busy, onCancel) { onSubmit(description, amount.toLongOrNull() ?: 0, paymentType, broker, distance.toIntOrNull()) }
}

/** 운임·결제 방식·주행거리 고치기 (LEDG-003). 거리는 운행을 마칠 때 자동으로 재지만 틀리거나 비었으면 여기서 고친다 */
@Composable
fun ColumnScope.FareForm(form: LedgerForm.EditFare, busy: Boolean, onSubmit: (Long, PaymentType, Int?) -> Unit, onCancel: () -> Unit) {
    var amount by rememberSaveable(form.entryId) { mutableStateOf(form.fare.toString()) }
    var paymentType by rememberSaveable(form.entryId) { mutableStateOf(form.paymentType) }
    var distance by rememberSaveable(form.entryId) { mutableStateOf(form.distanceKm?.toString().orEmpty()) }

    SheetTitle("장부 고치기")
    Spacer(Modifier.height(24.dp))
    AmountField(amount, label = "운임", onChange = { amount = it })
    Spacer(Modifier.height(16.dp))
    PaymentTypePicker(paymentType, onSelect = { paymentType = it })
    Spacer(Modifier.height(16.dp))
    DistanceField(distance, onChange = { distance = it })
    FormButtons(busy, onCancel) { onSubmit(amount.toLongOrNull() ?: 0, paymentType, distance.toIntOrNull()) }
}

/** 주행거리(km). 숫자만, 비워 두면 모르는 것으로 */
@Composable
private fun DistanceField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit).take(4)) },
        label = { Text("주행거리 (선택)", fontSize = 24.sp) },
        suffix = { Text("km", fontSize = 24.sp, color = TextSub) },
        textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PaymentTypePicker(selected: PaymentType, onSelect: (PaymentType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentType.entries.forEach { FilterChip(it.label, selected = selected == it, onClick = { onSelect(it) }) }
    }
}

/** 숫자만 받는 금액 칸. 입력한 금액을 "15만 원"처럼 함께 보여 준다 */
@Composable
private fun AmountField(value: String, label: String = "금액", onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit).take(9)) },
        label = { Text(label, fontSize = 24.sp) },
        suffix = { Text("원", fontSize = 24.sp, color = TextSub) },
        supportingText = value.toLongOrNull()?.takeIf { it > 0 }?.let { { Text(formatWon(it), fontSize = 24.sp, color = TextSub) } },
        textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 24.sp) },
        textStyle = TextStyle(fontSize = 24.sp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormButtons(busy: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
        IeoPrimaryButton(text = if (busy) "저장 중…" else "저장", onClick = { if (!busy) onSave() }, modifier = Modifier.weight(2f))
    }
}

/** "지울까요?" 확인. 되돌릴 수 없어서 한 번 더 묻는다 */
@Composable
fun ColumnScope.DeleteForm(form: LedgerForm.ConfirmDelete, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    SheetTitle(form.title)
    Spacer(Modifier.height(12.dp))
    Text(form.detail, fontSize = 24.sp, color = TextSub)
    Spacer(Modifier.height(8.dp))
    Text("지우면 되돌릴 수 없어요.", fontSize = 24.sp, color = TextSub)
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
        IeoPrimaryButton(text = if (busy) "지우는 중…" else "지우기", onClick = { if (!busy) onConfirm() }, modifier = Modifier.weight(2f))
    }
}

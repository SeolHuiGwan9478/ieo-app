package com.ieo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.ui.component.FilterChip
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import kotlin.math.roundToInt

/** 높이를 ± 버튼으로 0.1m씩 바꾼다. 정차 중에도 키보드 없이 고칠 수 있게 */
private const val HEIGHT_STEP = 0.1
private const val DEFAULT_HEIGHT = 3.5

@Composable
fun ColumnScope.NameForm(form: SettingsForm.EditName, busy: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember(form) { mutableStateOf(form.current) }
    SheetTitle("이름 바꾸기")
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30) },
        label = { Text("이름", fontSize = 18.sp) },
        textStyle = TextStyle(fontSize = 22.sp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    FormButtons(busy, onCancel = onCancel, onSave = { onSubmit(name) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.VehicleForm(
    vehicle: Vehicle,
    busy: Boolean,
    onSubmit: (Tonnage, BodyType, Double?) -> Unit,
    onSetDefault: () -> Unit,
    onCancel: () -> Unit,
) {
    var tonnage by remember(vehicle) { mutableStateOf(vehicle.tonnage) }
    var bodyType by remember(vehicle) { mutableStateOf(vehicle.bodyType) }
    var height by remember(vehicle) { mutableStateOf(vehicle.heightMeters) }

    SheetTitle("${vehicle.plateNumber} 고치기")
    Spacer(Modifier.height(24.dp))
    FieldLabel("톤수")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tonnage.entries.forEach { FilterChip(it.label, selected = tonnage == it, onClick = { tonnage = it }) }
    }
    Spacer(Modifier.height(20.dp))
    FieldLabel("차종")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyType.entries.forEach { FilterChip(it.label, selected = bodyType == it, onClick = { bodyType = it }) }
    }
    Spacer(Modifier.height(20.dp))
    FieldLabel("높이 (짐칸 위까지)")
    HeightStepper(height = height, onChange = { height = it })
    Text("상하차지 높이 제한과 비교해 알려 드려요", fontSize = 18.sp, color = TextSub)
    if (!vehicle.isDefault) {
        Spacer(Modifier.height(20.dp))
        IeoTonalButton(text = "기본 차량으로 정하기", onClick = { if (!busy) onSetDefault() })
    }
    FormButtons(busy, onCancel = onCancel, onSave = { onSubmit(tonnage, bodyType, height) })
}

/**
 * 다음 배차 추천 설정 창. 키보드 없이 고치도록 반경은 ± 5km, 최소 실수익은 고르기로 한다.
 * 추천을 끄면 반경·실수익 칸을 숨긴다 (값은 그대로 저장)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.RecommendationForm(
    current: RecommendationPreference,
    busy: Boolean,
    onSubmit: (RecommendationPreference) -> Unit,
    onCancel: () -> Unit,
) {
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var radiusKm by remember(current) { mutableStateOf(current.radiusKm) }
    var minNetProfit by remember(current) { mutableStateOf(current.minNetProfit) }

    SheetTitle("다음 배차 추천")
    Spacer(Modifier.height(24.dp))
    FieldLabel("추천")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip("켬", selected = enabled, onClick = { enabled = true })
        FilterChip("끔", selected = !enabled, onClick = { enabled = false })
    }
    if (enabled) {
        Spacer(Modifier.height(20.dp))
        FieldLabel("찾는 범위 (하차지에서)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "−", onClick = { radiusKm = stepRadius(radiusKm, -RADIUS_STEP_KM) }, modifier = Modifier.width(88.dp))
            Text(
                text = "${radiusKm}km",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextStrong,
                style = Tnum,
                modifier = Modifier.width(140.dp),
            )
            IeoTonalButton(text = "+", onClick = { radiusKm = stepRadius(radiusKm, RADIUS_STEP_KM) }, modifier = Modifier.width(88.dp))
        }
        Text("넓히면 후보가 늘지만 빈 차로 가는 거리도 길어져요", fontSize = 18.sp, color = TextSub)
        Spacer(Modifier.height(20.dp))
        FieldLabel("최소 실수익 (운임에서 공차 기름값을 뺀 것)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MIN_NET_PROFIT_CHOICES.forEach { won ->
                FilterChip(minNetProfitChip(won), selected = minNetProfit == won, onClick = { minNetProfit = won })
            }
        }
    } else {
        Spacer(Modifier.height(12.dp))
        Text("다음 배차를 음성과 카드로 알려 드리지 않아요", fontSize = 18.sp, color = TextSub)
    }
    FormButtons(busy, onCancel = onCancel, onSave = { onSubmit(RecommendationPreference(enabled, radiusKm, minNetProfit)) })
}

/** 반경을 바꾸는 단위 */
private const val RADIUS_STEP_KM = 5

/** 최소 실수익 고르기. null은 조건 없음 */
internal val MIN_NET_PROFIT_CHOICES: List<Long?> = listOf(null, 100_000, 200_000, 300_000, 400_000, 500_000)

private fun minNetProfitChip(won: Long?): String = won?.let { "${it / 10_000}만 원" } ?: "조건 없음"

/** 5km 단위로 맞추고 5~100km 안으로 자른다 */
internal fun stepRadius(radiusKm: Int, delta: Int): Int {
    val next = (radiusKm + delta) / RADIUS_STEP_KM * RADIUS_STEP_KM
    return next.coerceIn(RecommendationPreference.MIN_RADIUS_KM, RecommendationPreference.MAX_RADIUS_KM)
}

@Composable
fun ColumnScope.SignOutConfirm(onSignOut: () -> Unit, onCancel: () -> Unit) {
    SheetTitle("로그아웃할까요?")
    Spacer(Modifier.height(12.dp))
    Text("다시 쓰려면 휴대폰으로 QR을 찍거나 인증번호로 로그인해요", fontSize = 21.sp, color = TextSub)
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
        IeoPrimaryButton(text = "로그아웃", onClick = onSignOut, modifier = Modifier.weight(2f))
    }
}

/** null이면 "모름". 처음 누르면 3.5m에서 시작한다 */
@Composable
private fun HeightStepper(height: Double?, onChange: (Double?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "−", onClick = { onChange(step(height, -HEIGHT_STEP)) }, modifier = Modifier.width(88.dp))
        Text(
            text = height?.let(::formatMeters) ?: "모름",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (height == null) TextSub else TextStrong,
            style = Tnum,
            modifier = Modifier.width(140.dp),
        )
        IeoTonalButton(text = "+", onClick = { onChange(step(height, HEIGHT_STEP)) }, modifier = Modifier.width(88.dp))
        if (height != null) IeoTonalButton(text = "모름", onClick = { onChange(null) })
    }
    Spacer(Modifier.height(8.dp))
}

/** 0.1m 단위로 맞추고 1.5~4.2m 안으로 자른다 */
internal fun step(height: Double?, delta: Double): Double {
    val next = (height ?: return DEFAULT_HEIGHT) + delta
    val rounded = (next * 10).roundToInt() / 10.0
    return rounded.coerceIn(Vehicle.MIN_HEIGHT_METERS, Vehicle.MAX_HEIGHT_METERS)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextStrong)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun FormButtons(busy: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
        IeoPrimaryButton(text = if (busy) "저장 중…" else "저장", onClick = { if (!busy) onSave() }, modifier = Modifier.weight(2f))
    }
}

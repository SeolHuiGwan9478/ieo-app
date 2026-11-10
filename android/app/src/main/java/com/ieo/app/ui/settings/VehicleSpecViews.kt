package com.ieo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.DimensionRange
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.VehicleDimensions
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.component.TruckIcon
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.DisabledGray
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange600
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextMid
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

/*
 * 차량 수정 창의 입력 부품. 휴대폰 앱(ui/component/VehicleSpecInputs, 가입 화면)과 같은 구성을
 * 차량 화면 기준(글자 24sp 이상, 누르는 곳 76dp)으로 키웠다.
 */

private val Shape = RoundedCornerShape(16.dp)
private val TouchSize = 76.dp

/** 큰 톤수 표시 양옆의 −/+. 끝에서는 그 쪽 버튼이 꺼진다 */
@Composable
internal fun TonnageStepper(tonnage: Tonnage, onChange: (Tonnage) -> Unit) {
    val all = Tonnage.entries
    val index = all.indexOf(tonnage)
    Row(
        Modifier.fillMaxWidth().border(1.dp, DisabledGray, Shape).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("−", "톤수 내리기", enabled = index > 0) { onChange(all[index - 1]) }
        Text(tonnage.label, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        RoundButton("+", "톤수 올리기", enabled = index < all.lastIndex) { onChange(all[index + 1]) }
    }
}

/** 차종: 트럭 그림이 있는 네모 칸, 3열 */
@Composable
internal fun BodyTypeTiles(selected: BodyType, onSelect: (BodyType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BodyType.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { type ->
                    val on = type == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            // 넓은 화면에서 칸이 커지지 않게 높이는 고정
                            .height(168.dp)
                            .background(if (on) Orange50 else CanvasGray.copy(alpha = 0f), Shape)
                            .border(if (on) 3.dp else 1.dp, if (on) Orange600 else DisabledGray, Shape)
                            .clickable(role = Role.RadioButton) { onSelect(type) }
                            .semantics { this.selected = on },
                    ) {
                        TruckIcon(type, width = 128.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(type.label, fontSize = 24.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Orange700 else TextStrong)
                    }
                }
            }
        }
    }
}

/** 높이와 너비·길이·총중량·축 수 (모두 선택). 테두리 하나 안의 목록, 줄마다 −/+와 비우기 */
@Composable
internal fun VehicleSpecList(
    height: Double?,
    dimensions: VehicleDimensions,
    onHeight: (Double?) -> Unit,
    onDimensions: (VehicleDimensions) -> Unit,
) {
    val d = dimensions
    Column(Modifier.fillMaxWidth().border(1.dp, DisabledGray, Shape)) {
        SpecRow("높이", height?.let(::formatMeters),
            onMinus = { onHeight(step(height, -0.1)) }, onPlus = { onHeight(step(height, 0.1)) }, onClear = { onHeight(null) })
        Line()
        RangeRow("너비", d.widthCm, VehicleDimensions.WIDTH, ::formatCmAsMeters) { onDimensions(d.copy(widthCm = it)) }
        Line()
        RangeRow("길이", d.lengthCm, VehicleDimensions.LENGTH, ::formatCmAsMeters) { onDimensions(d.copy(lengthCm = it)) }
        Line()
        RangeRow("총중량", d.totalWeightKg, VehicleDimensions.TOTAL_WEIGHT, ::formatKgAsTons) { onDimensions(d.copy(totalWeightKg = it)) }
        Line()
        RangeRow("축 수", d.axleCount, VehicleDimensions.AXLES, { "${it}축" }) { onDimensions(d.copy(axleCount = it)) }
    }
}

@Composable
private fun Line() = HorizontalDivider(color = DisabledGray, modifier = Modifier.padding(horizontal = 20.dp))

@Composable
private fun RangeRow(label: String, value: Int?, range: DimensionRange, format: (Int) -> String, onChange: (Int?) -> Unit) =
    SpecRow(label, value?.let(format),
        onMinus = { onChange(range.next(value, -range.step)) },
        onPlus = { onChange(range.next(value, range.step)) },
        onClear = { onChange(null) })

@Composable
private fun SpecRow(label: String, value: String?, onMinus: () -> Unit, onPlus: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong, modifier = Modifier.weight(1f))
        RoundButton("−", "$label 줄이기", enabled = true, onClick = onMinus)
        Text(
            value ?: "모름",
            fontSize = 28.sp,
            fontWeight = if (value == null) FontWeight.Normal else FontWeight.Bold,
            color = if (value == null) TextSub else TextStrong,
            style = Tnum,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        RoundButton("+", "$label 늘리기", enabled = true, onClick = onPlus)
        // ×는 늘 보이고, 값이 있을 때만 눌린다 (모름이면 흐리게)
        Box(Modifier.size(TouchSize).clickable(enabled = value != null, role = Role.Button, onClickLabel = "$label 비우기", onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, contentDescription = "$label 비우기", tint = if (value != null) TextSub else DisabledGray, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun RoundButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(TouchSize).clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(60.dp).background(if (enabled) CanvasGray else CanvasGray.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = if (enabled) TextMid else DisabledGray)
        }
    }
}

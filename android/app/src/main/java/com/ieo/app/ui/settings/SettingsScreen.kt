package com.ieo.app.ui.settings

import com.ieo.app.ui.theme.Orange600
import com.ieo.app.R
import com.ieo.app.ui.theme.TextMid
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ieo.app.domain.account.DriverStanding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.account.Profile
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.ui.common.formatWon
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleDimensions
import com.ieo.app.ui.component.DetailLine
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.StatusTag
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

private val ScreenPadding = 28.dp

/**
 * 설정 탭: 내 정보 / 내 차량 / 단골 주선사 / 앱. 고치기는 아래에서 올라오는 창에서 한다.
 * 상태는 [SettingsViewModel]이 정하고, 이 화면은 그리기만 한다.
 * [linksSection]: 단골 주선사 칸. 상태를 따로 가진 ui/links가 그린다
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    linkCount: Int = 0,
    linksSection: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().background(CanvasGray)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding)
                .widthIn(max = 880.dp),
        ) {
            ProfileSection(state.profile, state.standing, linkCount, onEdit = { actions.openForm(SettingsForm.EditName(state.profile?.name.orEmpty())) })
            VehicleSection(state.vehicles, isLoading = state.isLoading, onEdit = { actions.openForm(SettingsForm.EditVehicle(it)) })
            RecommendationSection(state.recommendation, onEdit = { actions.openForm(SettingsForm.EditRecommendation(it)) })
            linksSection()
            AppSection(state.appVersion, onSignOut = { actions.openForm(SettingsForm.ConfirmSignOut) })
        }
        IeoBottomSheet(item = state.form, onDismiss = actions::close) { form ->
            when (form) {
                is SettingsForm.EditName -> NameForm(form, busy = state.isBusy, onSubmit = actions::saveName, onCancel = actions::close)
                is SettingsForm.EditVehicle -> VehicleForm(
                    vehicle = form.vehicle,
                    busy = state.isBusy,
                    onSubmit = actions::saveVehicle,
                    onCancel = actions::close,
                )
                is SettingsForm.EditRecommendation -> RecommendationForm(
                    current = form.current,
                    busy = state.isBusy,
                    onSubmit = actions::saveRecommendation,
                    onCancel = actions::close,
                )
                SettingsForm.ConfirmSignOut -> SignOutConfirm(onSignOut = actions::signOut, onCancel = actions::close)
            }
        }
    }
}

/** 휴대폰 앱 내 정보처럼 얼굴 그림, 이름과 인증 마크, 주선사에게 보이는 내 기록 */
@Composable
private fun ProfileSection(profile: Profile?, standing: DriverStanding?, linkCount: Int, onEdit: () -> Unit) {
    WhiteSection {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)) {
            Image(painterResource(R.drawable.ic_avatar_driver), contentDescription = null, modifier = Modifier.size(88.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(profile?.name ?: "-", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
                    // 휴대폰 앱과 같은 인증 마크
                    if (standing?.verified == true) VerifiedBadge()
                }
                Text(profile?.phone?.let(::maskPhone) ?: "-", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(top = 4.dp))
            }
        }
        standing?.let { RecordCard(it, linkCount) }
        Spacer(Modifier.height(16.dp))
        IeoTonalButton(text = "내 정보 수정", onClick = onEdit)
    }
}

/** 휴대폰 앱 내 정보의 "주선사에게 보이는 내 기록"과 같은 칸. 서류는 휴대폰에서 올리므로 누르는 곳은 없다 */
@Composable
private fun RecordCard(standing: DriverStanding, linkCount: Int) {
    Column(
        Modifier
            .padding(horizontal = 8.dp)
            .border(2.dp, CanvasGray, RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("주선사에게 보이는 내 기록", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextMid, modifier = Modifier.weight(1f))
            Text(documentsSummary(standing), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange700)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            RecordStat("${standing.completedTripCount}건", "완료 운행", Modifier.weight(1f))
            RecordStat("${standing.lateCancelCount}건", "직전 취소", Modifier.weight(1f))
            RecordStat("${linkCount}곳", "단골 주선사", Modifier.weight(1f))
        }
        if (!standing.verified) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).background(Orange50, RoundedCornerShape(14.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_menu_document), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("서류를 올리면 인증 차주 마크가 붙어요. 휴대폰 앱 내 정보 > 서류에서 올려요", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Orange700)
            }
        }
    }
}

/** 휴대폰 앱과 같은 짧은 서류 상태: "인증됨" 또는 "승인 2/4" */
internal fun documentsSummary(standing: DriverStanding): String =
    if (standing.verified) "인증됨" else "승인 ${standing.approvedRequired}/${standing.requiredTotal}"

/** 주선사가 보는 내 기록 한 칸: 큰 숫자와 이름 (휴대폰 앱 Stat과 같은 모양) */
@Composable
private fun RecordStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
        Text(label, fontSize = 24.sp, color = TextSub)
    }
}

@Composable
private fun VehicleSection(vehicles: List<Vehicle>, isLoading: Boolean, onEdit: (Vehicle) -> Unit) {
    WhiteSection {
        // 차주 한 명에 차 한 대 (VEH-001). 예전에 여러 대를 넣은 계정이면 기본 차량만 보여 준다
        SectionTitle(title = "내 차량", note = "번호 변경은 휴대폰 앱에서", icon = R.drawable.ic_menu_truck)
        val vehicle = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
        if (vehicle == null) {
            Text(if (isLoading) "불러오는 중…" else "휴대폰 앱에서 내 차를 등록해 주세요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(8.dp))
        } else {
            VehicleRow(vehicle, onClick = { onEdit(vehicle) })
        }
    }
}

@Composable
private fun VehicleRow(vehicle: Vehicle, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(vehicle.plateNumber, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            if (vehicle.pleosLinked) StatusTag(text = "이 차와 연결됨", color = TextSub, background = CanvasGray)
        }
        Spacer(Modifier.height(4.dp))
        Text(vehicleSummary(vehicle), fontSize = 24.sp, color = TextSub)
        Spacer(Modifier.height(10.dp))
        IeoTonalButton(text = "수정", onClick = onClick)
    }
}

/** 다음 배차 추천 설정 (REC-004, 025). 못 받았으면 버튼 없이 안내만 */
@Composable
private fun RecommendationSection(preference: RecommendationPreference?, onEdit: (RecommendationPreference) -> Unit) {
    WhiteSection {
        SectionTitle(title = "다음 배차 추천", note = "운송 중 음성으로, 하차지에서 카드로 알려 드려요", icon = R.drawable.ic_menu_recommend)
        if (preference == null) {
            Text("설정을 불러오지 못했어요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(8.dp))
            return@WhiteSection
        }
        DetailLine("추천", if (preference.enabled) "켬" else "끔")
        if (preference.enabled) {
            DetailLine("찾는 범위", "하차지에서 ${preference.radiusKm}km 안")
            DetailLine("최소 실수익", minNetProfitLabel(preference.minNetProfit))
            DetailLine("빼는 화물", excludedCargoLabel(preference.excludedCargo))
        }
        Spacer(Modifier.height(12.dp))
        IeoTonalButton(text = "수정", onClick = { onEdit(preference) })
    }
}

/** 이름 옆 "인증 차주" 표시. 휴대폰 앱과 같은 주황 마크 */
@Composable
internal fun VerifiedBadge() {
    Row(
        Modifier.background(Orange50, RoundedCornerShape(50)).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = Orange600, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text("인증 차주", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange700)
    }
}

/** "냉동·냉장, 이사". 없으면 "없음" */
internal fun excludedCargoLabel(cargo: Set<CargoCategory>): String =
    cargo.sortedBy { it.ordinal }.joinToString(", ") { it.label }.ifEmpty { "없음" }

internal fun minNetProfitLabel(won: Long?): String = won?.let { "${formatWon(it)} 이상" } ?: "조건 없음"

@Composable
private fun AppSection(appVersion: String, onSignOut: () -> Unit) {
    WhiteSection {
        SectionTitle(title = "앱", icon = R.drawable.ic_menu_car_login)
        DetailLine("버전", appVersion)
        Spacer(Modifier.height(12.dp))
        IeoTonalButton(text = "로그아웃", onClick = onSignOut)
    }
}

/** "5톤 카고 · 높이 3.6m · 연비 4.2km/L". 모르는 값은 뺀다 */
internal fun vehicleSummary(vehicle: Vehicle): String = listOfNotNull(
    "${vehicle.tonnage.label} ${vehicle.bodyType.label}",
    vehicle.heightMeters?.let { "높이 ${formatMeters(it)}" } ?: "높이 모름",
    vehicle.measuredKmPerLiter?.let { "연비 ${"%.1f".format(it)}km/L" },
).joinToString(" · ")

internal fun formatMeters(meters: Double): String = "%.1fm".format(meters)

/** 010-1234-5678 → 010-****-5678. 차량 화면은 옆 사람도 보므로 가운데를 가린다 */
internal fun maskPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length < 10) return phone
    return "${digits.take(3)}-****-${digits.takeLast(4)}"
}

@Preview(widthDp = 1100, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    IeoTheme {
        SettingsScreen(
            state = SettingsUiState(
                profile = Profile(name = "김기사", phone = "01012345678"),
                vehicles = listOf(
                    Vehicle(1, "경기80바1234", Tonnage.TON_5, BodyType.CARGO, 3.6, 4.2, pleosLinked = true, isDefault = true),
                    Vehicle(2, "경기81사5678", Tonnage.TON_1, BodyType.BOX, null, null, pleosLinked = false, isDefault = false),
                ),
                recommendation = RecommendationPreference(
                    enabled = true,
                    radiusKm = 30,
                    minNetProfit = 200_000,
                    excludedCargo = setOf(CargoCategory.MOVING),
                ),
                appVersion = "0.1.0",
            ),
            actions = PreviewSettingsActions,
        )
    }
}

private object PreviewSettingsActions : SettingsActions {
    override fun refresh() = Unit
    override fun openForm(form: SettingsForm) = Unit
    override fun close() = Unit
    override fun saveName(name: String) = Unit
    override fun saveVehicle(tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?, dimensions: VehicleDimensions) = Unit
    override fun saveRecommendation(preference: RecommendationPreference) = Unit
    override fun signOut() = Unit
}

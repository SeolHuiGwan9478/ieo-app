package com.ieo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ieo.app.domain.account.Profile
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.ui.carlink.CarLinkActions
import com.ieo.app.ui.carlink.CarLinkSheet
import com.ieo.app.ui.carlink.CarLinkUiState
import com.ieo.app.ui.carlink.VehicleChoice
import com.ieo.app.ui.home.DeliveryProofSheet
import com.ieo.app.ui.home.HomeScreen
import com.ieo.app.ui.home.NextOrderOfferModal
import com.ieo.app.ui.home.SampleHomeData
import com.ieo.app.ui.home.StartGuidanceSheet
import com.ieo.app.ui.home.WaitProofSheet
import com.ieo.app.ui.ledger.DayGroup
import com.ieo.app.ui.ledger.EntryDetail
import com.ieo.app.ui.ledger.EntryRow
import com.ieo.app.ui.ledger.ExpenseRow
import com.ieo.app.ui.ledger.LedgerActions
import com.ieo.app.ui.ledger.LedgerForm
import com.ieo.app.ui.ledger.LedgerScreen
import com.ieo.app.ui.ledger.LedgerTab
import com.ieo.app.ui.ledger.LedgerUiState
import com.ieo.app.ui.ledger.MonthSummary
import com.ieo.app.ui.ledger.PayState
import com.ieo.app.ui.ledger.ReceivableSummary
import com.ieo.app.ui.ledger.WaitFeeRow
import com.ieo.app.ui.links.DriverLinksActions
import com.ieo.app.ui.links.LinkInvitationSheet
import com.ieo.app.ui.links.LinkRow
import com.ieo.app.ui.links.UnlinkSheet
import com.ieo.app.ui.login.LoginScreen
import com.ieo.app.ui.login.PhoneLoginActions
import com.ieo.app.ui.login.PhoneLoginUiState
import com.ieo.app.ui.login.QrLoginActions
import com.ieo.app.ui.login.QrLoginUiState
import com.ieo.app.ui.orders.OrderDetail
import com.ieo.app.ui.orders.OrderRow
import com.ieo.app.ui.orders.OrdersActions
import com.ieo.app.ui.orders.OrdersScreen
import com.ieo.app.ui.orders.OrdersUiState
import com.ieo.app.ui.register.RegisterTripActions
import com.ieo.app.ui.register.RegisterTripSheet
import com.ieo.app.ui.register.RegisterTripUiState
import com.ieo.app.ui.settings.SettingsActions
import com.ieo.app.ui.settings.SettingsForm
import com.ieo.app.ui.settings.SettingsScreen
import com.ieo.app.ui.settings.SettingsUiState
import com.ieo.app.ui.theme.IeoTheme
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 동작을 눌러도 아무 일도 하지 않는 구현 */
private inline fun <reified T : Any> noop(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(self)
            "equals" -> self === args?.get(0)
            "toString" -> T::class.java.simpleName
            else -> if (method.returnType == java.lang.Boolean.TYPE) false else null
        }
    } as T

/** 화면을 앱 영역 크기(전체·내비 옆 반 화면·다크)로 그려 글자가 잘리지 않는지 눈으로 본다. 결과는 build/screens */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1900dp-h1300dp-land-mdpi")
class ScreenshotTest(private val window: String, private val scene: String) {

    @get:Rule val rule = createComposeRule()

    @Test
    fun capture() {
        val dark = window == "dark"
        val widthDp = if (window == "wide") 1683 else 840
        val draw = Scenes.all.getValue(scene)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IeoTheme(darkTheme = dark) {
                Row(Modifier.size(widthDp.dp, 1208.dp).background(if (dark) Color(0xFF1E1E1E) else Color.White).testTag("frame")) {
                    // 왼쪽 메뉴 자리
                    Spacer(Modifier.width(112.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) { draw() }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        if (scene == "login_phone") {
            rule.onNodeWithText("휴대폰 번호", useUnmergedTree = true).performClick()
            rule.mainClock.advanceTimeBy(1_000)
        }
        rule.onNodeWithTag("frame").captureRoboImage("build/screens/$window/$scene.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> =
            listOf("half", "wide", "dark").flatMap { w -> Scenes.all.keys.map { arrayOf<Any>(w, it) } }
    }
}

private object Scenes {
    private val trip = SampleHomeData.myTrips[0]
    private val vehicle = Vehicle(1, "경기80바1234", Tonnage.values().first { it.label == "5톤" }, BodyType.values().first(), 3.6, null, true, true)
    private val preference = RecommendationPreference()
    private val row = EntryRow(1, "평택 물류센터 → 대전 산업단지", "인수증", "한빛물류 · 12월 5일 지급 예정", PayState.OVERDUE, "32만 원", "+ 대기비 1만 원 청구 예정")
    private val expense = ExpenseRow(1, "주유", "11월 5일", "12만 원")
    private val ledger = LedgerUiState(
        monthLabel = "11월",
        summary = MonthSummary("1,250만 원", "210만 원", "1,040만 원"),
        receivable = ReceivableSummary("92만 원", "60만 원", "3만 원"),
        days = listOf(DayGroup("11월 5일 (목) · 1건", listOf(row), listOf(expense))),
        receivableRows = listOf(row),
        expenseRows = listOf(expense),
    )
    private val detail = EntryDetail(
        1, "평택 물류센터 → 대전 산업단지",
        listOf("주선사" to "한빛물류", "운임" to "32만 원 · 인수증", "지급 예정" to "12월 5일"),
        false, listOf(WaitFeeRow(1, "상차 대기 1시간", "1만 원", false)), 320_000, PaymentType.RECEIPT,
    )
    private val orderRow = OrderRow(
        1, "17:03", "11월 12일", "대전 산업단지 → 부산 신항 CY", "5톤 카고", "인수증", "한빛물류", "58만 원",
        requested = false, distanceMeters = 4_000, netProfit = "실수익 약 58만 원",
    )
    private val orderDetail = OrderDetail(
        1, "대전 산업단지 → 부산 신항 CY",
        listOf(
            "상차" to "오늘 17:03 ~ 20:03 · 대전 대덕구 대화로 106",
            "하차" to "11월 13일 14:00까지 · 부산 강서구 신항로 300",
            "차량" to "5톤 카고", "화물" to "가전 박스 · 4.5톤 · 지게차",
            "운임" to "58만 원 · 인수증 · 30일 안에 지급",
            "실수익" to "497,000원 (기름값·통행료 뺀 금액)", "예상 통행료" to "17,000원", "기름값" to "66,000원", "상차지까지" to "4km", "주선사" to "한빛물류", "주선사 기록" to "기록 쌓는 중",
        ),
        requested = false,
    )
    private val settings = SettingsUiState(Profile("김기사", "01012345678"), listOf(vehicle), preference, "0.1.0")
    private val link = LinkRow(1, "한빛물류", "연결 요청 11월 9일")
    private val offer by lazy { SampleHomeData.unloading().nextOrders.first() }

    val all: Map<String, @Composable () -> Unit> = linkedMapOf(
        "home_idle" to { HomeScreen(SampleHomeData.idle()) },
        "home_waiting" to { HomeScreen(SampleHomeData.waiting()) },
        "home_unloading" to { HomeScreen(SampleHomeData.unloading()) },
        "home_driving" to { HomeScreen(SampleHomeData.driving()) },
        "sheet_guidance" to { Box(Modifier.fillMaxSize()) { StartGuidanceSheet(trip, {}, {}) } },
        "sheet_wait_proof" to { Box(Modifier.fillMaxSize()) { WaitProofSheet(WaitProofLink("https://ieo.example/w/abc123", Instant.parse("2026-11-12T09:00:00Z")), {}) } },
        "sheet_wait_proof_send" to { Box(Modifier.fillMaxSize()) { WaitProofSheet(WaitProofLink("https://ieo.example/w/abc123", Instant.parse("2026-11-12T09:00:00Z"), canSend = true), {}) } },
        "sheet_wait_proof_sent" to { Box(Modifier.fillMaxSize()) { WaitProofSheet(WaitProofLink("https://ieo.example/w/abc123", Instant.parse("2026-11-12T09:00:00Z"), canSend = true, sent = true), {}) } },
        "sheet_delivery_proof" to { Box(Modifier.fillMaxSize()) { DeliveryProofSheet(DeliveryProof("https://ieo.example/d/abc123", DeliveryProofStatus.PENDING, null, null), {}) } },
        "modal_next_order" to { Box(Modifier.fillMaxSize()) { NextOrderOfferModal(offer, false, false, {}, {}, {}) } },
        "sheet_register" to { Box(Modifier.fillMaxSize()) { RegisterTripSheet(true, RegisterTripUiState(), noop<RegisterTripActions>(), {}) } },
        "sheet_car_link" to { Box(Modifier.fillMaxSize()) { CarLinkSheet(CarLinkUiState(listOf(VehicleChoice(1, "경기80바1234", "5톤 카고"), VehicleChoice(2, "서울90사5678", "11톤 윙바디"))), noop<CarLinkActions>()) } },
        "sheet_link_invite" to { Box(Modifier.fillMaxSize()) { LinkInvitationSheet(link, false, noop<DriverLinksActions>()) } },
        "sheet_unlink" to { Box(Modifier.fillMaxSize()) { UnlinkSheet(link, false, noop<DriverLinksActions>()) } },
        "orders_list" to { OrdersScreen(OrdersUiState(nearby = true, rows = listOf(orderRow, orderRow.copy(id = 2, day = "오늘"))), noop<OrdersActions>(), false) },
        "orders_detail" to { OrdersScreen(OrdersUiState(nearby = true, rows = listOf(orderRow), detail = orderDetail), noop<OrdersActions>(), false) },
        "orders_report" to { OrdersScreen(OrdersUiState(nearby = true, rows = listOf(orderRow), detail = orderDetail, reporting = true), noop<OrdersActions>(), false) },
        "ledger_entries" to { LedgerScreen(ledger, noop<LedgerActions>()) },
        "ledger_receivables" to { LedgerScreen(ledger.copy(tab = LedgerTab.RECEIVABLES), noop<LedgerActions>()) },
        "ledger_expenses" to { LedgerScreen(ledger.copy(tab = LedgerTab.EXPENSES), noop<LedgerActions>()) },
        "ledger_detail" to { LedgerScreen(ledger.copy(detail = detail), noop<LedgerActions>()) },
        "ledger_add_expense" to { LedgerScreen(ledger.copy(form = LedgerForm.AddExpense), noop<LedgerActions>()) },
        "ledger_add_trip" to { LedgerScreen(ledger.copy(form = LedgerForm.AddTrip), noop<LedgerActions>()) },
        "ledger_edit_fare" to { LedgerScreen(ledger.copy(detail = detail, form = LedgerForm.EditFare(1, 320_000, PaymentType.RECEIPT)), noop<LedgerActions>()) },
        "settings" to { SettingsScreen(settings, noop<SettingsActions>()) },
        "settings_name" to { SettingsScreen(settings.copy(form = SettingsForm.EditName("김기사")), noop<SettingsActions>()) },
        "settings_vehicle" to { SettingsScreen(settings.copy(form = SettingsForm.EditVehicle(vehicle)), noop<SettingsActions>()) },
        "settings_vehicle_dims" to { SettingsScreen(settings.copy(form = SettingsForm.EditVehicle(vehicle.copy(dimensions = com.ieo.app.domain.vehicle.VehicleDimensions(249, 850, 11_000, 3)))), noop<SettingsActions>()) },
        "settings_recommend" to { SettingsScreen(settings.copy(form = SettingsForm.EditRecommendation(preference)), noop<SettingsActions>()) },
        "settings_sign_out" to { SettingsScreen(settings.copy(form = SettingsForm.ConfirmSignOut), noop<SettingsActions>()) },
        "login_qr" to { LoginScreen(PhoneLoginUiState(), noop<PhoneLoginActions>(), QrLoginUiState.Waiting("ABC123", "ieo://login/ABC123", Long.MAX_VALUE / 2), noop<QrLoginActions>()) },
        "login_phone" to { LoginScreen(PhoneLoginUiState(phone = "01012345678", code = "123", codeExpiresAt = Long.MAX_VALUE / 2), noop<PhoneLoginActions>(), QrLoginUiState.Loading, noop<QrLoginActions>()) },
    )
}

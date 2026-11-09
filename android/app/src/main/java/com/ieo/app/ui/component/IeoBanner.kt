package com.ieo.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.TextMid
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.CardSurface
import kotlinx.coroutines.delay

/** 배너가 뜻하는 것. 색만으로 구분하지 않도록 아이콘 모양도 다르다 */
enum class BannerKind { GOOD, INFO }

/** 화면 위에 잠깐 보여 주는 한 건. [visibleMs]가 지나면 저절로 사라진다 */
data class IeoBanner(val id: Long, val text: String, val kind: BannerKind, val visibleMs: Long)

/** 버튼을 누른 결과처럼 짧게 알리는 말. 시스템 토스트가 Pleos 에뮬레이터에 그려지지 않아 앱 안에 띄운다 */
const val MESSAGE_VISIBLE_MS = 4_000L

/** 서버 알림. 두 줄을 읽을 수 있게 더 오래 둔다 */
const val NOTIFICATION_VISIBLE_MS = 8_000L

private val BannerShape = RoundedCornerShape(20.dp)

/** 화면들이 보낸 짧은 말을 들어온 순서대로 하나씩 보여 준다. 같은 말이 맨 뒤에 이미 있으면 다시 넣지 않는다 */
class MessageBanners {
    private val queue = mutableStateListOf<IeoBanner>()
    private var lastId = 0L

    val current: IeoBanner? get() = queue.firstOrNull()

    fun show(text: String) {
        if (queue.lastOrNull()?.text == text) return
        // 서버 알림 id(양수)와 겹치지 않게 음수를 쓴다
        queue += IeoBanner(id = --lastId, text = text, kind = BannerKind.INFO, visibleMs = MESSAGE_VISIBLE_MS)
    }

    fun done(id: Long) {
        queue.removeAll { it.id == id }
    }
}

/** 화면 상태의 한 번 보여 줄 말을 배너로 넘기고 지운다 */
@Composable
fun ShowMessage(message: String?, onShow: (String) -> Unit, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            onShow(message)
            onShown()
        }
    }
}

/** 마이크가 열려 있을 때 보여 준다. 듣는 줄 모르고 말하거나, 몰래 듣는다고 느끼지 않게 */
@Composable
fun ListeningPill(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .shadow(8.dp, BannerShape)
            .background(CardSurface, BannerShape)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = IeoStatus.colors.driving, modifier = Modifier.size(28.dp))
        Text("듣고 있어요", color = TextStrong, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 배너를 띄우고 시간이 지나면 [onDone]을 부른다. 주행 중에도 만질 필요 없이 저절로 사라진다.
 * 시간은 앱이 화면에 보이는 동안만 잰다: 내비가 앞에 있는 동안 온 알림은 앱으로 돌아왔을 때 보여 준다
 */
@Composable
fun IeoBannerHost(banner: IeoBanner?, onDone: (Long) -> Unit, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (banner != null) {
        LaunchedEffect(banner.id) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(banner.visibleMs)
                onDone(banner.id)
            }
        }
    }
    AnimatedContent(
        targetState = banner,
        transitionSpec = {
            (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        contentKey = { it?.id },
        contentAlignment = Alignment.TopCenter,
        label = "ieo-banner",
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    ) { shown ->
        // 바깥 칸이 폭을 꽉 채워 주므로, 카드가 글자만큼만 차지하도록 한 겹 감싼다
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
            if (shown != null) BannerCard(shown, onClose = { onDone(shown.id) })
        }
    }
}

@Composable
private fun BannerCard(banner: IeoBanner, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .widthIn(max = 760.dp)
            .heightIn(min = 76.dp)
            .shadow(12.dp, BannerShape)
            .background(CardSurface, BannerShape)
            .clickable(onClick = onClose)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // 색만으로 구분하지 않는다: 아이콘 모양과 문장이 뜻을 말한다
        Icon(
            imageVector = if (banner.kind == BannerKind.GOOD) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (banner.kind == BannerKind.GOOD) IeoStatus.colors.done else TextMid,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = banner.text,
            color = TextStrong,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            // 단어 중간에서 줄을 바꾸지 않는다
            style = TextStyle(lineBreak = LineBreak.Heading),
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "닫기",
            tint = TextMid,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Preview(widthDp = 960, heightDp = 240)
@Composable
private fun IeoBannerPreview() {
    IeoTheme {
        IeoBannerHost(
            banner = IeoBanner(1, "한빛물류에서 배차를 확정했어요. 김포 한강 물류에서 평택 물류센터까지, 내 배차에 넣었어요", BannerKind.GOOD, NOTIFICATION_VISIBLE_MS),
            onDone = {},
        )
    }
}

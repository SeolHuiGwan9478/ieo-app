package com.ieo.app.ui.common

import java.text.NumberFormat
import java.util.Locale

/** 금액 표시. 만 원 단위로 떨어지면 "45만 원", 아니면 "12,500원" */
fun formatWon(amount: Long): String =
    if (amount >= 10_000 && amount % 10_000 == 0L) {
        "${amount / 10_000}만 원"
    } else {
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    }

/** 경과 시간 표시. 01:42:10 */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

/** 남은 시간을 말로. "1시간 18분", "18분" */
fun formatMinutesKorean(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${rest}분"
        rest == 0L -> "${hours}시간"
        else -> "${hours}시간 ${rest}분"
    }
}

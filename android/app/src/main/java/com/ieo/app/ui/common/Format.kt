package com.ieo.app.ui.common

import java.text.NumberFormat
import java.util.Locale

/** 금액 표시. 만 원 단위로 떨어지면 "45만 원", 아니면 "12,500원" */
/** 운임이 정해지지 않은 배차를 "0원"으로 보이지 않게 한다 */
fun formatFare(amount: Long): String = if (amount == 0L) "운임 미정" else formatWon(amount)

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

/**
 * 받침에 맞는 조사를 붙인다: josa("한빛물류", "과", "와") → "한빛물류와", josa("대한운송", "이", "가") → "대한운송이".
 * 숫자는 읽는 소리로 본다 ("경기80바1234" → 사). "으로"는 ㄹ 받침 뒤에서 "로"가 된다.
 * 한글·숫자가 아닌 글자로 끝나면 받침이 있는 쪽을 쓴다.
 */
fun josa(word: String, withFinal: String, withoutFinal: String): String {
    val last = word.lastOrNull() ?: return word + withFinal
    val finalConsonant = when (last) {
        in '가'..'힣' -> (last - '가') % 28
        in '0'..'9' -> DIGIT_FINALS[last - '0']
        else -> -1
    }
    val particle = when {
        finalConsonant == 0 -> withoutFinal
        finalConsonant == RIEUL && withFinal == "으로" -> withoutFinal
        else -> withFinal
    }
    return word + particle
}

/** 받침 번호 (0 = 없음). ㄹ은 8 */
private const val RIEUL = 8

/** 영(ㅇ) 일(ㄹ) 이 삼(ㅁ) 사 오 육(ㄱ) 칠(ㄹ) 팔(ㄹ) 구 */
private val DIGIT_FINALS = intArrayOf(21, 8, 0, 16, 0, 0, 1, 8, 8, 0)

/** 들어서 알기 쉬운 금액 (UX-011): 372,229원 → "37만 원". 1만 원이 안 되면 그대로 */
fun formatWonSpoken(amount: Long): String {
    val manwon = Math.round(amount / 10_000.0)
    return if (manwon == 0L) formatWon(amount) else "${manwon}만 원"
}

package com.ieo.app.ui.login

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 숫자만 있는 휴대폰 번호에 하이픈을 넣는다.
 * - 11자리: 010-1234-5678
 * - 10자리: 011-123-4567
 * - 입력 중: 010, 010-1234, 010-1234-5 …
 */
fun formatPhoneNumber(digits: String): String {
    val d = digits.filter(Char::isDigit).take(11)
    return when {
        d.length <= 3 -> d
        d.length <= 7 -> "${d.substring(0, 3)}-${d.substring(3)}"
        d.length == 10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}"
        else -> "${d.substring(0, 3)}-${d.substring(3, 7)}-${d.substring(7)}"
    }
}

/**
 * 입력값은 숫자만 저장하고, 화면에만 하이픈을 넣어 보여 준다.
 * 커서 위치가 하이픈 때문에 어긋나지 않도록 숫자 위치와 화면 위치를 서로 바꿔 준다.
 */
class PhoneNumberVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatPhoneNumber(text.text)
        return TransformedText(AnnotatedString(formatted), PhoneOffsetMapping(formatted))
    }
}

internal class PhoneOffsetMapping(private val formatted: String) : OffsetMapping {

    /** 숫자 offset번째 앞의 화면 위치 */
    override fun originalToTransformed(offset: Int): Int {
        var digits = 0
        formatted.forEachIndexed { index, char ->
            if (char.isDigit()) {
                if (digits == offset) return index
                digits++
            }
        }
        return formatted.length
    }

    /** 화면 위치 앞에 있는 숫자 개수 */
    override fun transformedToOriginal(offset: Int): Int =
        formatted.take(offset).count(Char::isDigit)
}

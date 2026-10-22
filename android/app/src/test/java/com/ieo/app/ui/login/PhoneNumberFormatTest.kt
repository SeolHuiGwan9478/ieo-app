package com.ieo.app.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberFormatTest {

    @Test
    fun 입력_중에는_3_4_나머지로_나눈다() {
        assertEquals("", formatPhoneNumber(""))
        assertEquals("010", formatPhoneNumber("010"))
        assertEquals("010-1", formatPhoneNumber("0101"))
        assertEquals("010-1234", formatPhoneNumber("0101234"))
        assertEquals("010-1234-5", formatPhoneNumber("01012345"))
    }

    @Test
    fun 열한자리는_3_4_4() {
        assertEquals("010-1234-5678", formatPhoneNumber("01012345678"))
    }

    @Test
    fun 열자리는_3_3_4() {
        assertEquals("011-123-4567", formatPhoneNumber("0111234567"))
    }

    @Test
    fun 숫자가_아닌_글자와_열한자리_초과는_버린다() {
        assertEquals("010-1234-5678", formatPhoneNumber("010-1234-5678"))
        assertEquals("010-1234-5678", formatPhoneNumber("0101234567899"))
    }

    @Test
    fun 커서_위치는_하이픈을_건너뛴다() {
        val mapping = PhoneOffsetMapping(formatPhoneNumber("01012345678")) // 010-1234-5678
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(4, mapping.originalToTransformed(3)) // "010-|1234"
        assertEquals(13, mapping.originalToTransformed(11)) // 맨 끝
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(3, mapping.transformedToOriginal(3)) // 하이픈 바로 앞
        assertEquals(11, mapping.transformedToOriginal(13))
    }
}

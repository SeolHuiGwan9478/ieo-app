package com.ieo.app.domain.voice

/** 안내 뒤에 차주가 하는 대답 (REC-020, UX-020) */
enum class VoiceCommand {
    /** "응", "네", "맞아" */
    YES,

    /** "아니", "다음", "안 해" */
    NO,

    /** "수락", "받을게" */
    ACCEPT,

    /** "자세히" */
    DETAIL,

    /** "다시" */
    REPEAT,

    /** 알아듣지 못함 */
    UNKNOWN;

    companion object {
        private val NEGATIVE = listOf("아니", "안해", "안할", "안받", "싫어", "됐어", "다음", "넘겨", "거절")
        /** 전화 받겠다는 말이나 옆 사람과 하는 통화를 수락으로 듣지 않는다 */
        private val NOT_FOR_US = listOf("전화", "통화")
        private val ACCEPTS = listOf("수락", "받을게", "받겠", "접수")

        /** "할게"는 "내일 할게"처럼 어디에나 붙으므로 이 말 그대로일 때만 수락이다 */
        private val EXACT_ACCEPTS = setOf("할게", "할게요", "이거할게", "이거할게요", "그거할게", "그거할게요")
        private val DETAILS = listOf("자세히", "자세하게", "상세", "설명")
        private val REPEATS = listOf("다시", "뭐라고", "한번더")
        private val YES_WORDS = listOf("응", "어", "네", "넵", "예", "맞아", "그래", "좋아", "오케이")

        /** 잘못 알아듣고 배차를 수락하면 안 되므로, 아니라는 말을 먼저 보고 "응" 같은 말은 짧은 대답일 때만 인정한다 */
        fun parse(heard: String): VoiceCommand {
            val text = heard.replace(Regex("[\\s.,!?~]"), "")
            return when {
                text.isEmpty() || NOT_FOR_US.any(text::contains) -> UNKNOWN
                NEGATIVE.any(text::contains) -> NO
                DETAILS.any(text::contains) -> DETAIL
                REPEATS.any(text::contains) -> REPEAT
                ACCEPTS.any(text::contains) || text in EXACT_ACCEPTS -> ACCEPT
                text.length <= MAX_YES_LENGTH && YES_WORDS.any(text::startsWith) -> YES
                else -> UNKNOWN
            }
        }

        private const val MAX_YES_LENGTH = 5
    }
}

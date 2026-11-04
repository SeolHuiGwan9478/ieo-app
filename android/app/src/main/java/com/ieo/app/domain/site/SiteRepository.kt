package com.ieo.app.domain.site

import com.ieo.app.core.network.ApiResult

/** 상하차지. 정보·메모 남기기(SITE-004, 005)는 음성 입력과 함께 붙인다 */
interface SiteRepository {

    suspend fun card(siteId: Long): ApiResult<SiteCard>
}

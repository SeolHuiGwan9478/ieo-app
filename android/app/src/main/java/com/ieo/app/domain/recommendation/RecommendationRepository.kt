package com.ieo.app.domain.recommendation

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.vehicle.GeoPoint

/**
 * 오더 찾기의 "제외할 화물"과 배차 추천의 "제외할 화물"은 같은 설정이다 (DSP-010, REC-004).
 * 한쪽에서 바꾸면 다른 쪽에도 그대로 적용된다. 서버에는 추천 설정으로 저장한다 (휴대폰 앱과 같다)
 */
interface ExcludedCargoStore {
    /** 저장된 제외할 화물. 못 받으면 null */
    suspend fun load(): Set<CargoCategory>?

    suspend fun save(excluded: Set<CargoCategory>)

    /** 저장소 없이 쓸 때 (화면 미리보기·테스트) */
    object None : ExcludedCargoStore {
        override suspend fun load(): Set<CargoCategory>? = null
        override suspend fun save(excluded: Set<CargoCategory>) = Unit
    }
}

/** 추천 설정을 읽어 제외할 화물만 바꿔 저장한다 */
class PreferenceExcludedCargoStore(private val repository: RecommendationRepository) : ExcludedCargoStore {
    override suspend fun load(): Set<CargoCategory>? = (repository.preference() as? ApiResult.Success)?.data?.excludedCargo

    override suspend fun save(excluded: Set<CargoCategory>) {
        val current = (repository.preference() as? ApiResult.Success)?.data ?: return
        if (current.excludedCargo != excluded) repository.changePreference(current.copy(excludedCargo = excluded))
    }
}

interface RecommendationRepository {

    /**
     * 추천 최대 1건 (03 배차 추천). 서버가 지금 운행을 보고 상차지·하차지 근처, 운행이 없으면 세운 자리([near]) 근처를 찾는다.
     * 같은 곳에서 이미 받았으면 서버가 적어 둔 것을 fresh=false로 돌려준다. 추천을 껐으면 빈 목록
     */
    suspend fun recommend(near: GeoPoint): ApiResult<Recommendation>

    /** 내 추천 설정. 정한 적 없으면 서버 기본값 */
    suspend fun preference(): ApiResult<RecommendationPreference>

    suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference>
}

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
     * 차를 세운 자리([near]) 근처 추천, 실수익 높은 순 3건 (03 배차 추천). 추천을 껐으면 빈 목록.
     * 서버는 보여 준 것을 기록하지 않는다
     */
    suspend fun recommend(near: GeoPoint): ApiResult<List<NextOrder>>

    /** 내 추천 설정. 정한 적 없으면 서버 기본값 */
    suspend fun preference(): ApiResult<RecommendationPreference>

    suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference>
}

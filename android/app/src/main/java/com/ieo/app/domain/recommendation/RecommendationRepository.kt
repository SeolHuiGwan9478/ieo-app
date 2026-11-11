package com.ieo.app.domain.recommendation

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.order.DispatchRequest

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

    /** 운송 중인 운행 다음에 이어서 할 배차 후보. 운송 전이거나 추천을 껐으면 빈 목록 */
    suspend fun recommend(tripId: Long): ApiResult<NextOrders>

    /**
     * 안내한 추천과 대답을 서버에 적는다. [answer]가 null이면 안내만 하고 대답이 없었던 것.
     * 수락이면 배차 요청이 가고 그 요청을 돌려준다
     */
    suspend fun answer(tripId: Long, orderId: Long, answer: RecommendationAnswer?): ApiResult<DispatchRequest?>

    /** 내 추천 설정. 정한 적 없으면 서버 기본값 */
    suspend fun preference(): ApiResult<RecommendationPreference>

    suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference>
}

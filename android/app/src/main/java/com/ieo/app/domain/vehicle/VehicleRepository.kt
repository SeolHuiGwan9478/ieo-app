package com.ieo.app.domain.vehicle

import com.ieo.app.core.network.ApiResult

/** 차량 추가·삭제는 휴대폰 앱에서 한다. 차량 화면에서는 고치기와 기본 차량 바꾸기만 */
interface VehicleRepository {

    suspend fun vehicles(): ApiResult<List<Vehicle>>

    /** 톤수·차종·높이를 한 번에 바꾼다. 높이를 null로 보내면 비운다 */
    suspend fun update(vehicleId: Long, tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?): ApiResult<Vehicle>

    /** 배차 요청·운행 등록에 쓰는 기본 차량 */
    suspend fun setDefault(vehicleId: Long): ApiResult<Vehicle>

    /** 차량 앱에 로그인한 뒤: 이 트럭(차대번호)이 내 차량 중 어느 것인지 찾는다 (VEH-004) */
    suspend fun resolvePleos(vin: String): ApiResult<PleosMatch>

    /** [PleosMatch.Select]에서 고른 차량에 이 트럭을 잇고 기본 차량으로 정한다 */
    suspend fun linkPleos(vehicleId: Long, vin: String): ApiResult<Vehicle>
}

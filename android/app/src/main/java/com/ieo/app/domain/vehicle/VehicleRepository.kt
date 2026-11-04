package com.ieo.app.domain.vehicle

import com.ieo.app.core.network.ApiResult

/** 차량 추가·삭제는 휴대폰 앱에서 한다. 차량 화면에서는 고치기와 기본 차량 바꾸기만 */
interface VehicleRepository {

    suspend fun vehicles(): ApiResult<List<Vehicle>>

    /** 톤수·차종·높이를 한 번에 바꾼다. 높이를 null로 보내면 비운다 */
    suspend fun update(vehicleId: Long, tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?): ApiResult<Vehicle>

    /** 배차 요청·운행 등록에 쓰는 기본 차량 */
    suspend fun setDefault(vehicleId: Long): ApiResult<Vehicle>
}

package com.ieo.app.data.order

import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderSite
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/dispatch/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class OrderResponse(
    val id: Long,
    val brokerName: String,
    val pickup: OrderSiteResponse,
    val dropoff: OrderSiteResponse,
    val pickupFrom: String,
    val tonnageLabel: String,
    val bodyTypeLabel: String,
    val fare: Long,
    val paymentTypeLabel: String,
    val distanceM: Long? = null,
) {
    fun toDomain() = Order(
        id = id,
        brokerName = brokerName,
        pickup = pickup.toDomain(),
        dropoff = dropoff.toDomain(),
        pickupFrom = Instant.parse(pickupFrom),
        tonnageLabel = tonnageLabel,
        bodyTypeLabel = bodyTypeLabel,
        fare = fare,
        paymentTypeLabel = paymentTypeLabel,
        distanceMeters = distanceM,
    )
}

@Serializable
data class OrderSiteResponse(val name: String, val address: String, val lat: Double, val lng: Double) {
    fun toDomain() = OrderSite(name = name, address = address, latitude = lat, longitude = lng)
}

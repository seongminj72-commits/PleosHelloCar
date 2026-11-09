package com.example.pleoshellocar.shared.data

import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.navigation.GeoPoint
interface StoreRepository { fun stores(): List<Store> }
class MockStoreRepository : StoreRepository {
    override fun stores(): List<Store> {
        // 메뉴별 추가 제조시간(데모 추정값): 아메리카노·콜드브루 0분, 카페라떼 1분, 바닐라라떼 2분.
        // 아메리카노 1잔 + 매장 A(기본 6분)는 기존 시연 기준(ETA 8 · 준비 6 · 여유 1 → 약 1분 후)과 같다.
        val menu = listOf(MenuItem("americano", "아메리카노", 3500), MenuItem("latte", "카페라떼", 4500, 1),
            MenuItem("vanilla", "바닐라라떼", 5000, 2), MenuItem("coldbrew", "콜드브루", 4500))
        return listOf(Store("A", "DriveFlow Coffee A", 6, true, menu, GeoPoint(37.5665, 126.9780)),
            Store("B", "DriveFlow Coffee B", 5, true, menu, GeoPoint(37.5700, 126.9830)), Store("C", "DriveFlow Coffee C", 8, false, menu, GeoPoint(37.5600, 126.9750)))
    }
}

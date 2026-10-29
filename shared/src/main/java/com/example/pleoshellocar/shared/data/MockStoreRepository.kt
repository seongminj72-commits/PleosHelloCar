package com.example.pleoshellocar.shared.data

import com.example.pleoshellocar.shared.domain.*
interface StoreRepository { fun stores(): List<Store> }
class MockStoreRepository : StoreRepository {
    override fun stores(): List<Store> {
        val menu = listOf(MenuItem("americano", "아메리카노", 3500), MenuItem("latte", "카페라떼", 4500),
            MenuItem("vanilla", "바닐라라떼", 5000), MenuItem("coldbrew", "콜드브루", 4500))
        return listOf(Store("A", "DriveFlow Coffee A", 6, true, menu),
            Store("B", "DriveFlow Coffee B", 5, true, menu), Store("C", "DriveFlow Coffee C", 8, false, menu))
    }
}

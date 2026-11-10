package com.example.pleoshellocar.shared.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.CarToast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.vehicle.*
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.*

/** MVP의 자체 행 수 제한. 호스트별 제약과 카테고리 심사는 별도 검증한다. */
abstract class FlowScreen(context: CarContext, protected val flow: DriveFlowController) : Screen(context) {
    private val etaChanged: () -> Unit = { invalidate() }
    private val vehicleChanged: () -> Unit = { invalidate() }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { flow.navigation.addListener(etaChanged); flow.vehicle.addListener(vehicleChanged) }
        override fun onStop(owner: LifecycleOwner) { flow.navigation.removeListener(etaChanged); flow.vehicle.removeListener(vehicleChanged) }
        override fun onResume(owner: LifecycleOwner) { invalidate() }
    }) }

    protected fun runAction(block: () -> Unit) {
        try { block() } catch(e: IllegalStateException) { toast(e.message ?: "상태를 확인하세요.") }
        catch(e: IllegalArgumentException) { toast(e.message ?: "입력값을 확인하세요.") }
        invalidate()
    }

    protected fun toast(text: String) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    /** 행 생성 - 차량용 디스플레이 가독성 고려: 제목 굵게, 본문 일반 */
    protected fun row(
        title: String,
        text: String,
        parked: Boolean = false,
        click: (() -> Unit)? = null
    ): Row {
        val builder = Row.Builder()
            .setTitle(title)
            .addText(text)
        if (click != null) {
            val listener = OnClickListener { runAction(click) }
            builder.setOnClickListener(if (parked) ParkedOnlyOnClickListener.create(listener) else listener)
        }
        return builder.build()
    }

    /** 액션 버튼 생성 - 주행 중 비활성화 지원 */
    protected fun action(title: String, parked: Boolean = false, click: () -> Unit): Action {
        val listener = OnClickListener { runAction(click) }
        return Action.Builder().setTitle(title)
            .setOnClickListener(if (parked) ParkedOnlyOnClickListener.create(listener) else listener).build()
    }

    /** 숫자 포맷: 3자리마다 콤마 */
    protected fun formatWon(won: Int): String = won.toString().replace("\\B(?=(\\d{3})+(?!\\d))".toRegex(), ",")

    /** 리스트 템플릿 - 최대 6행 제한 준수 */
    protected fun list(
        title: String,
        rows: List<Row>,
        root: Boolean = false,
        action: Action? = null
    ): Template {
        require(rows.size <= 6) { "ListTemplate 행 초과: ${rows.size} > 6" }
        val items = ItemList.Builder(); rows.forEach(items::addItem)
        val builder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(if (root) Action.APP_ICON else Action.BACK)
            .setSingleList(items.build())
        if (action != null) {
            builder.setActionStrip(ActionStrip.Builder().addAction(action).build())
        }
        return builder.build()
    }

    /** 페인 템플릿 - 최대 5행 제한 준수 */
    protected fun pane(title: String, rows: List<Row>, actions: List<Action> = emptyList()): Template {
        require(rows.size <= 5) { "PaneTemplate 행 초과: ${rows.size} > 5" }
        require(actions.size <= 2) { "PaneTemplate 액션 초과: ${actions.size} > 2" }
        val paneBuilder = Pane.Builder(); rows.forEach(paneBuilder::addRow)
        actions.forEach(paneBuilder::addAction)
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .build()
    }

    /** Mock 상태 표시 - 실제 차량 신호와 명확히 구분 */
    protected fun mockBadge(): String = when (flow.vehicle.source) {
        VehicleStateSource.MOCK -> "[데모] Mock 차량 상태: ${flow.vehicle.getVehicleState()} · 실제 차량 신호 아님"
        VehicleStateSource.VEHICLE_SDK -> "실제 차량 신호: ${flow.vehicle.getVehicleState()} (${flow.vehicle.reason})"
    }
}

class HomeScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template = list("Pleos DriveFlow", listOf(
        row("매장 찾기", "드라이브스루 매장 3곳 (가상)") { screenManager.push(StoreScreen(carContext, flow)) },
        row("내 주문 확인", flow.orders.latest()?.let { "${it.id} · ${it.status.label}" } ?: "주문 내역 없음") {
            screenManager.push(OrderStatusScreen(carContext, flow))
        },
        row("서비스 상태", mockBadge(), parked = true) { screenManager.push(DemoSettingsScreen(carContext, flow)) }
    ), root = true)
}

class StoreScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val storeRows = flow.stores.stores().map { store ->
            val status = if (store.acceptingOrders) "주문 가능" else "영업 종료"
            row(store.name, "준비 약 ${store.preparationMinutes}분 · $status (가상 매장)", parked = true) {
                flow.selectStore(store)
                screenManager.push(MenuScreen(carContext, flow))
            }
        }
        // 최대 6행 제한: 매장 3개 + 안내 행
        val rows = storeRows + listOf(row("안내", "정차 중에만 매장 선택 가능 (데모)", parked = true) {})
        return list("드라이브스루 매장 선택", rows)
    }
}

class MenuScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val menuRows = flow.selectedStore!!.menu.map { item ->
            val qty = flow.quantity(item.id)
            val qtyText = when (qty) {
                0 -> "선택 안 함"
                1 -> "1개"
                2 -> "2개"
                else -> "3개"
            }
            row(item.name, "${formatWon(item.priceWon)}원 · $qtyText", parked = true) {
                flow.setQuantity(item, (qty + 1) % 4)
            }
        }
        val recommendation = flow.recommendation()
        val recRow = row("주문 추천", "${flow.navigation.statusLabel} · ${recommendation.label} · ${recommendation.reason}", parked = false)
        val totalRow = row("합계", "${formatWon(flow.totalWon())}원", parked = false)

        // 최대 6행: 메뉴 4개 + 추천 + 합계
        val rows = menuRows + listOf(recRow, totalRow)

        return list("메뉴 선택", rows, action = action("주문 확인", parked = true) {
            InteractionPolicy.requireParked(flow.vehicle)
            check(flow.lines().isNotEmpty()) { "최소 1개 이상 선택하세요." }
            screenManager.push(ConfirmationScreen(carContext, flow))
        })
    }
}

class ConfirmationScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val recommendation = flow.recommendation()
        val orderSummary = flow.lines().joinToString(", ") { "${it.item.name} ${it.quantity}개" }
        val totalWon = formatWon(flow.totalWon())

        val rows = listOf(
            row(flow.selectedStore!!.name, "${totalWon}원 · $orderSummary"),
            row("주문 추천", "${flow.navigation.statusLabel} · ${recommendation.label} · ${recommendation.reason}"),
            row("결제 안내", "데모 결제 — 실제 청구 없음 [${mockBadge()}]"),
            row("차량 상태", mockBadge())
        )

        val actions = listOf(
            action("결제 진행", parked = true) {
                val order = flow.checkout()
                screenManager.push(ReceiptScreen(carContext, flow, order.id))
            },
            action("메뉴로 돌아가기", parked = true) { screenManager.pop() }
        )

        return pane("주문 확인", rows, actions)
    }
}

/** 구매 태스크의 마지막 템플릿 (5번째). 주문 상태는 홈에서 별도 태스크로 확인. */
class ReceiptScreen(context: CarContext, flow: DriveFlowController, private val id: String) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        "주문번호: $id\n데모 결제 — 실제 금액이 청구되지 않습니다."
    )
        .setTitle("모의 결제 성공")
        .setHeaderAction(Action.APP_ICON)
        .addAction(action("홈으로") { screenManager.popToRoot() })
        .build()
}

class OrderStatusScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val order = flow.orders.latest()
        val orderInfo = order?.let { "${it.id} · ${it.store.name} · ${formatWon(it.totalWon)}원" } ?: "주문 내역 없음"
        val pickupStatus = order?.status?.label ?: "먼저 주문하세요"

        val rows = listOf(
            row("주문 정보", orderInfo),
            row("픽업 상태", pickupStatus),
            row("알림", "${flow.announcement} · [텍스트만, 음성 미지원]"),
            row("진행 / 재시도", "정차 전용 · 실제 청구 없음", parked = true) {
                if (flow.orders.latest()?.status == OrderStatus.ORDER_CREATED) flow.checkout() else flow.advance()
            },
            row("주문 취소", "정차 전용 · 완료/취소된 주문은 변경 불가", parked = true) { flow.cancel() }
        )

        return list("내 주문 현황", rows)
    }
}

class DemoSettingsScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val vehicle = flow.vehicle as? SwitchableVehicleStateProvider
        val mock = vehicle?.mock ?: (flow.vehicle as? MockVehicleStateProvider)
        val navigation = (flow.navigation as? TmapNavigationEtaProvider)?.mock
            ?: (flow.navigation as? MockNavigationEtaProvider)
        val payment = flow.payment as? MockPaymentSimulator
        val stateRow = if (flow.vehicle.source == VehicleStateSource.MOCK && mock != null) {
            row("차량 상태", "${mock.state} · 탭하여 순환 (Mock)", parked = true) {
                if (vehicle != null) vehicle.cycleMockState() else {
                    mock.state = when (mock.state) {
                        VehicleState.UNKNOWN -> VehicleState.PARKED
                        VehicleState.PARKED -> VehicleState.DRIVING
                        VehicleState.DRIVING -> VehicleState.UNKNOWN
                    }
                }
            }
        } else row("실제 신호: ${flow.vehicle.getVehicleState()}", flow.vehicle.reason)

        val rows = listOf(
            stateRow,
            if (vehicle != null) row("차량 상태 모드: ${if (vehicle.mode == VehicleStateMode.MOCK) "Mock" else "실제 차량"}",
                "모드 전환 / 차량 권한 · 실패 시 UNKNOWN", parked = true) {
                screenManager.push(VehicleModeScreen(carContext, flow))
            } else row("차량 상태 모드", mockBadge()),
            if (navigation != null) row("Mock ETA 설정", "${navigation.minutes?.let { "$it 분" } ?: "정보 없음"} · 탭하여 변경 (8→10→3→없음)", parked = true) {
                InteractionPolicy.requireParked(flow.vehicle)
                val values = listOf(8, 10, 3, null)
                navigation.minutes = values[(values.indexOf(navigation.minutes) + 1) % values.size]
            } else row("ETA 상태", flow.navigation.statusLabel),
            row("준비시간 / 예상 지연", "준비 ${flow.preparationOverride?.let { "$it 분" } ?: "매장 기본값"} · 지연 ${flow.possibleDelay}분 · 여유 1분", parked = true) {
                screenManager.push(TimingSettingsScreen(carContext, flow))
            },
            if (payment != null) row("다음 결제 실패 시뮬레이션", "${if (payment.failNext) "실패로 설정됨" else "정상"} · 탭하여 전환", parked = true) {
                InteractionPolicy.requireParked(flow.vehicle)
                payment.failNext = !payment.failNext
            } else row("결제 상태", "모의 결제 설정 없음"),
            row("ETA 연결 설정", flow.navigation.statusLabel, parked = true) {
                InteractionPolicy.requireParked(flow.vehicle)
                screenManager.push(EtaSettingsScreen(carContext, flow))
            }
        )
        return list("서비스 상태 / 데모 설정", rows)
    }
}

/** Separate timing controls keep the parent list within its six-row limit. */
class TimingSettingsScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template = list("준비시간 / 예상 지연", listOf(
        row("매장 준비시간 덮어쓰기", "${flow.preparationOverride?.let { "$it 분" } ?: "매장 기본값 사용"} · 기본→5→6→기본", parked = true) {
            InteractionPolicy.requireParked(flow.vehicle)
            flow.preparationOverride = when (flow.preparationOverride) { null -> 5; 5 -> 6; else -> null }
        },
        row("예상 지연 / 안전 여유", "지연 ${flow.possibleDelay}분 · 여유 1분 (추정값)", parked = true) {
            InteractionPolicy.requireParked(flow.vehicle)
            flow.possibleDelay = if (flow.possibleDelay == 0) 2 else 0
        }
    ))
}

class EtaSettingsScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val navigation = flow.navigation as? TmapNavigationEtaProvider
            ?: return list("ETA 설정", listOf(row("Mock", "실제 연결 공급자가 없습니다.")))
        return list("ETA 연결 설정", listOf(
            row("모드: ${navigation.mode}", navigation.statusLabel, parked = true) {
                InteractionPolicy.requireParked(flow.vehicle)
                navigation.setMode(if (navigation.mode == EtaMode.MOCK) EtaMode.REAL else EtaMode.MOCK)
            },
            row("위치 권한 허용", "Real 모드에서 현재 위치를 프록시와 TMAP에 전송합니다.", parked = true) {
                InteractionPolicy.requireParked(flow.vehicle)
                carContext.requestPermissions(listOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)) { _, _ -> navigation.refresh(); invalidate() }
            },
            row("ETA 새로고침", "30초 호출 간격 · 실패 시 Mock 자동 대체 없음", parked = true) {
                InteractionPolicy.requireParked(flow.vehicle); navigation.refresh()
            },
            row("연동 범위", "실제 경로 계산 + 가상 매장 테스트 좌표. 주문·결제·차량은 데모입니다.")
        ))
    }
}

/** Permission recovery must remain available while the provider reports UNKNOWN. */
class VehicleModeScreen(context: CarContext, flow: DriveFlowController) : FlowScreen(context, flow) {
    override fun onGetTemplate(): Template {
        val vehicle = flow.vehicle as? SwitchableVehicleStateProvider
            ?: return list("차량 상태 모드", listOf(row("차량 상태", mockBadge())))
        val rows = mutableListOf(
            row("모드: ${vehicle.mode}", mockBadge(), parked = true) {
                vehicle.setMode(if (vehicle.mode == VehicleStateMode.MOCK) VehicleStateMode.REAL else VehicleStateMode.MOCK)
            }
        )
        if (vehicle.canRequestVehiclePermissions) rows.add(
            row("차량 속도 권한 허용", "AAOS 차량 API · 거부하면 실제 상태 UNKNOWN", parked = true) {
                vehicle.requestVehiclePermissions()
            }
        )
        return list("차량 상태 모드", rows)
    }
}

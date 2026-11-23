package com.example.pleoshellocar.shared.domain

data class CompletedOrderSnapshot(val storeId: String, val lines: List<Pair<String, Int>>)

sealed interface ParsedCommand {
    data class Scheduled(val item: MenuItem, val quantity: Int, val minutesBeforeArrival: Int,
        val warning: String? = null) : ParsedCommand
    data class Immediate(val item: MenuItem, val quantity: Int) : ParsedCommand
    data class Reorder(val previous: CompletedOrderSnapshot) : ParsedCommand
    data object Cancel : ParsedCommand
    data class Unknown(val reason: String) : ParsedCommand
}

object VoiceCommandParser {
    private val quantities = mapOf("한" to 1, "하나" to 1, "두" to 2, "둘" to 2,
        "세" to 3, "셋" to 3, "네" to 4, "넷" to 4)

    fun parse(text: String, menu: List<MenuItem>, lastOrder: CompletedOrderSnapshot?,
        preparationMinutes: Int = 6): ParsedCommand {
        val compact = normalize(text)
        if (compact.isBlank()) return ParsedCommand.Unknown("명령을 입력하세요")
        if (compact.contains("취소")) return ParsedCommand.Cancel
        if (compact.contains("지난번") || compact.contains("늘먹던"))
            return lastOrder?.let(ParsedCommand::Reorder) ?: ParsedCommand.Unknown("완료된 이전 주문이 없습니다")

        val matches = menu.filter { compact.contains(normalize(it.name)) }
        if (matches.isEmpty()) return ParsedCommand.Unknown("현재 매장 메뉴를 찾을 수 없습니다")
        if (matches.size != 1) return ParsedCommand.Unknown("메뉴를 하나만 지정해 주세요")
        val item = matches.single()
        val withoutItem = compact.replace(normalize(item.name), "")
        val amountTokens = Regex("([0-9]+|하나|한|둘|두|셋|세|넷|네)(잔|개)").findAll(withoutItem).toList()
        if (amountTokens.size > 1) return ParsedCommand.Unknown("수량을 하나만 지정해 주세요")
        val count = amountTokens.singleOrNull()?.groupValues?.get(1)?.let { it.toIntOrNull() ?: quantities[it] } ?: 1
        if (count !in 1..9) return ParsedCommand.Unknown("수량은 1~9잔으로 지정해 주세요")
        if (compact.contains("도착")) {
            val leadText = Regex("도착(.+?)분전").find(compact)?.groupValues?.get(1)
                ?: return ParsedCommand.Unknown("도착 몇 분 전인지 알려 주세요")
            val lead = leadText.toIntOrNull() ?: koreanMinutes(leadText)
                ?: return ParsedCommand.Unknown("한글 분 숫자는 1~99분만 지원합니다")
            if (lead !in 1..180) return ParsedCommand.Unknown("전달 시점은 1~180분 전으로 지정하세요")
            val warning = if (lead < preparationMinutes + item.extraPreparationMinutes)
                "도착까지 준비되지 않을 수 있습니다" else null
            return ParsedCommand.Scheduled(item, count, lead, warning)
        }
        if (listOf("지금", "바로", "주문", "줘").any(compact::contains))
            return ParsedCommand.Immediate(item, count)
        return ParsedCommand.Unknown("주문 시점을 지정해 주세요")
    }

    /** Sino-Korean minute words from 일 to 구십구; larger words remain Unknown. */
    private fun koreanMinutes(value: String): Int? {
        val digits = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5,
            '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        if (value.length == 1) return digits[value.single()] ?: if (value == "십") 10 else null
        val tensAt = value.indexOf('십')
        if (tensAt !in 0..1 || value.lastIndex > tensAt + 1) return null
        val tens = if (tensAt == 0) 1 else digits[value.first()] ?: return null
        val ones = value.getOrNull(tensAt + 1)?.let { digits[it] ?: return null } ?: 0
        return tens * 10 + ones
    }

    private fun normalize(value: String) = value.lowercase().replace("\\s+".toRegex(), "")
        .replace("라테", "라떼")
}

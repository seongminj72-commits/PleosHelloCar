package com.example.pleoshellocar.shared

import com.example.pleoshellocar.shared.domain.*
import org.junit.Assert.*
import org.junit.Test

class VoiceCommandParserTest {
    private val menu = listOf(MenuItem("americano", "아메리카노", 3500),
        MenuItem("latte", "카페라떼", 4500, 1))

    @Test fun scheduledLeadAndWarning() {
        val parsed = VoiceCommandParser.parse("도착 10분 전에 아메리카노 준비해 줘", menu, null)
        assertEquals(ParsedCommand.Scheduled(menu[0], 1, 10), parsed)
        assertEquals("도착까지 준비되지 않을 수 있습니다",
            (VoiceCommandParser.parse("도착 3분 전에 아메리카노 준비해 줘", menu, null) as ParsedCommand.Scheduled).warning)
    }

    @Test fun immediateQuantityAndSpelling() {
        assertEquals(ParsedCommand.Immediate(menu[1], 2),
            VoiceCommandParser.parse("카페 라테 두 잔 지금 주문해 줘", menu, null))
        assertEquals(ParsedCommand.Immediate(menu[0], 9),
            VoiceCommandParser.parse("아메리카노 9잔 지금 주문해 줘", menu, null))
    }

    @Test fun reorderCancelAndUnknown() {
        val previous = CompletedOrderSnapshot("A", listOf("americano" to 1))
        assertEquals(ParsedCommand.Reorder(previous), VoiceCommandParser.parse("늘 먹던 걸로", menu, previous))
        assertTrue(VoiceCommandParser.parse("지난번이랑 똑같이", menu, null) is ParsedCommand.Unknown)
        assertEquals(ParsedCommand.Cancel, VoiceCommandParser.parse("예약 취소", menu, null))
        assertTrue(VoiceCommandParser.parse("없는 메뉴 지금", menu, null) is ParsedCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("", menu, null) is ParsedCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("아메리카노 카페라떼 지금", menu, null) is ParsedCommand.Unknown)
    }
}

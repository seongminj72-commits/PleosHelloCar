package com.example.pleoshellocar.probe

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * 문서에 시그니처가 없는 SDK 부분을 이름 추측 없이 다루기 위한 도구.
 * 결과 로그가 곧 "실제 API 형태"의 근거가 된다.
 */
object ProbeReflection {

    /** 클래스의 public 메서드 시그니처 목록(Object 메서드 제외). */
    fun describeMethods(type: Class<*>): String =
        type.methods
            .filter { it.declaringClass != Any::class.java }
            .sortedBy { it.name }
            .joinToString(prefix = "\n  ", separator = "\n  ") { sig(it) }

    /** 인자 없는 get*Info() 메서드를 모두 호출하고 반환값을 기록한다. */
    fun invokeInfoGetters(target: Any, log: (String) -> Unit) {
        val getters = target.javaClass.methods.filter {
            it.name.startsWith("get") && it.name.endsWith("Info") && it.parameterCount == 0 &&
                Modifier.isPublic(it.modifiers)
        }.sortedBy { it.name }
        if (getters.isEmpty()) log("[NAVI] get*Info() 형태의 공개 메서드 없음")
        getters.forEach { m ->
            try {
                val result = m.invoke(target)
                log("[NAVI] ${m.name}() -> ${if (m.returnType == Void.TYPE) "void" else dump(result)}")
            } catch (t: Throwable) {
                val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
                log("[NAVI] ${m.name}() threw ${cause.javaClass.name}: ${cause.message}")
            }
        }
    }

    /** 인터페이스의 모든 콜백을 기록하는 프록시. 콜백 인자는 필드 단위로 펼쳐 남긴다. */
    @Suppress("UNCHECKED_CAST")
    fun <T> loggingProxy(
        type: Class<T>,
        tag: String,
        log: (String) -> Unit,
        onCall: (String) -> Unit = {}
    ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when {
            method.declaringClass == Any::class.java -> when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                else -> "ProbeProxy(${type.simpleName})"
            }
            else -> {
                val rendered = args?.joinToString { dump(it) } ?: ""
                log("[$tag] ${method.name}($rendered) thread=${Thread.currentThread().name}")
                runCatching { onCall(method.name) }
                defaultReturn(method.returnType)
            }
        }
    } as T

    /**
     * target.[addMethod](listener) 의 인자 타입을 SDK에서 직접 읽어 프록시를 만든다.
     * TTS의 EventListener, STT의 ResultListener처럼 패키지 경로가 문서에 없는 경우에 쓴다.
     */
    fun registerListener(
        target: Any,
        addMethod: String,
        tag: String,
        log: (String) -> Unit,
        onCall: (String) -> Unit
    ) {
        val method = target.javaClass.methods.firstOrNull {
            it.name == addMethod && it.parameterCount == 1 && it.parameterTypes[0].isInterface
        }
        if (method == null) { log("[$tag] $addMethod(interface) 메서드 없음"); return }
        val listenerType = method.parameterTypes[0]
        log("[$tag] listener type = ${listenerType.name}")
        try {
            method.invoke(target, loggingProxy(listenerType, tag, log, onCall))
            log("[$tag] $addMethod ok")
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            log("[$tag] $addMethod threw ${cause.javaClass.name}: ${cause.message}")
        }
    }

    /** 객체를 toString과 필드 목록으로 펼친다(중첩 1단계). ETA·거리 필드 이름 확인용. */
    fun dump(value: Any?, depth: Int = 0): String {
        if (value == null) return "null"
        val type = value.javaClass
        if (type.isPrimitive || value is Number || value is CharSequence || value is Boolean ||
            value is Enum<*> || depth >= 2) return value.toString()
        if (value is Collection<*>) return value.take(5).joinToString(prefix = "[", postfix = "]") { dump(it, depth + 1) } +
            if (value.size > 5) " …(${value.size})" else ""
        val fields = generateSequence<Class<*>>(type) { it.superclass }
            .takeWhile { it != Any::class.java && !it.name.startsWith("android.") }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) }
            .toList()
        if (fields.isEmpty()) return "${type.name}:$value"
        return fields.joinToString(prefix = "${type.simpleName}{", postfix = "}") { f ->
            val v = runCatching { f.isAccessible = true; f.get(value) }.getOrElse { "<${it.javaClass.simpleName}>" }
            "${f.name}=${dump(v, depth + 1)}"
        }
    }

    private fun sig(m: Method) =
        "${m.returnType.simpleName} ${m.name}(${m.parameterTypes.joinToString { it.name }})"

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        Void.TYPE -> null
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        Character.TYPE -> '\u0000'
        Unit::class.java -> Unit
        else -> null
    }
}

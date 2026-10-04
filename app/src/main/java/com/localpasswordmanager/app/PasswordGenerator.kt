package com.localpasswordmanager.app

import java.security.SecureRandom

internal enum class PasswordComplexity {
    SIMPLE, NORMAL, COMPLEX;

    val defaultLength: Int
        get() = when (this) {
            SIMPLE -> 12
            NORMAL -> 20
            COMPLEX -> 24
        }

    val defaultTypes: Set<PasswordCharacterType>
        get() = when (this) {
            SIMPLE -> setOf(PasswordCharacterType.LOWERCASE, PasswordCharacterType.DIGITS)
            NORMAL -> setOf(
                PasswordCharacterType.LOWERCASE,
                PasswordCharacterType.UPPERCASE,
                PasswordCharacterType.DIGITS,
            )
            COMPLEX -> PasswordCharacterType.entries.toSet()
        }
}

internal enum class PasswordCharacterType(val characters: String) {
    LOWERCASE(('a'..'z').joinToString("")),
    UPPERCASE(('A'..'Z').joinToString("")),
    DIGITS("0123456789"),
    SYMBOLS((('!'..'/') + (':'..'@') + ('['..'`') + ('{'..'~')).joinToString("")),
}

internal data class PasswordRules(val length: Int, val types: Set<PasswordCharacterType>) {
    init {
        require(length in 8..128) { "密码长度须为 8–128 位" }
        require(types.isNotEmpty()) { "请至少选择一种字符类型" }
    }
}

internal class PasswordGenerator {
    private val random = SecureRandom()

    fun generate(rules: PasswordRules): String {
        val types = rules.types.toSet()
        require(types.isNotEmpty()) { "请至少选择一种字符类型" }
        val pool = types.joinToString("") { it.characters }
        val candidate = CharArray(rules.length)
        try {
            // Rejection keeps all valid passwords equally likely while guaranteeing every selected type.
            do {
                for (index in candidate.indices) {
                    candidate[index] = pool[random.nextInt(pool.length)]
                }
            } while (types.any { type -> candidate.none { it in type.characters } })
            return candidate.concatToString()
        } finally {
            candidate.fill('\u0000')
        }
    }
}

internal data class PasswordGeneratorOptions(
    val complexity: PasswordComplexity = PasswordComplexity.NORMAL,
    val advanced: Boolean = false,
    val customComplexity: PasswordComplexity? = null,
    val customLength: String = "",
    val customTypes: Set<PasswordCharacterType> = emptySet(),
) {
    fun resolve(): PasswordRules {
        val activeComplexity = if (advanced) customComplexity ?: PasswordComplexity.NORMAL else complexity
        val length = if (advanced && customLength.isNotBlank()) {
            require(customLength.all { it in '0'..'9' }) { "请输入 8–128 的整数长度" }
            requireNotNull(customLength.toIntOrNull()) { "请输入 8–128 的整数长度" }
        } else {
            activeComplexity.defaultLength
        }
        val types = if (advanced && customTypes.isNotEmpty()) customTypes else activeComplexity.defaultTypes
        return PasswordRules(length, types.toSet())
    }
}

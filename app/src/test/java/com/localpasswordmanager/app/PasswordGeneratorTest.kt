package com.localpasswordmanager.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test fun defaultOptionsUseNormalComplexityWithoutAdvancedCustomization() {
        val options = PasswordGeneratorOptions()

        assertEquals(PasswordComplexity.NORMAL, options.complexity)
        assertEquals(false, options.advanced)
        assertEquals(
            PasswordRules(20, setOf(
                PasswordCharacterType.LOWERCASE,
                PasswordCharacterType.UPPERCASE,
                PasswordCharacterType.DIGITS,
            )),
            options.resolve(),
        )
    }

    @Test fun presetsSpecifyLengthAndCharacterTypes() {
        assertEquals(
            PasswordRules(12, setOf(PasswordCharacterType.LOWERCASE, PasswordCharacterType.DIGITS)),
            PasswordGeneratorOptions(complexity = PasswordComplexity.SIMPLE).resolve(),
        )
        assertEquals(
            PasswordRules(24, PasswordCharacterType.entries.toSet()),
            PasswordGeneratorOptions(complexity = PasswordComplexity.COMPLEX).resolve(),
        )
    }

    @Test fun disabledAdvancedOptionsNeverChangeOrInvalidateThePreset() {
        val options = PasswordGeneratorOptions(
            complexity = PasswordComplexity.SIMPLE,
            customComplexity = PasswordComplexity.COMPLEX,
            customLength = "not a length",
            customTypes = setOf(PasswordCharacterType.SYMBOLS),
        )

        assertEquals(
            PasswordRules(12, setOf(PasswordCharacterType.LOWERCASE, PasswordCharacterType.DIGITS)),
            options.resolve(),
        )
    }

    @Test fun emptyAdvancedOptionsUseNormalDefaultsIndependentlyOfThePreset() {
        for (preset in PasswordComplexity.entries) {
            assertEquals(
                PasswordRules(20, setOf(
                    PasswordCharacterType.LOWERCASE,
                    PasswordCharacterType.UPPERCASE,
                    PasswordCharacterType.DIGITS,
                )),
                PasswordGeneratorOptions(complexity = preset, advanced = true).resolve(),
            )
        }
    }

    @Test fun customComplexitySuppliesDefaultsForOmittedLengthAndTypes() {
        assertEquals(
            PasswordRules(12, setOf(PasswordCharacterType.LOWERCASE, PasswordCharacterType.DIGITS)),
            PasswordGeneratorOptions(advanced = true, customComplexity = PasswordComplexity.SIMPLE).resolve(),
        )
        assertEquals(
            PasswordRules(24, PasswordCharacterType.entries.toSet()),
            PasswordGeneratorOptions(
                advanced = true,
                customComplexity = PasswordComplexity.COMPLEX,
                customLength = " \t",
            ).resolve(),
        )
    }

    @Test fun eachAdvancedOverrideIsOptionalAndIndependent() {
        assertEquals(
            PasswordRules(35, setOf(
                PasswordCharacterType.LOWERCASE,
                PasswordCharacterType.UPPERCASE,
                PasswordCharacterType.DIGITS,
            )),
            PasswordGeneratorOptions(advanced = true, customLength = "35").resolve(),
        )
        assertEquals(
            PasswordRules(24, setOf(PasswordCharacterType.DIGITS)),
            PasswordGeneratorOptions(
                advanced = true,
                customComplexity = PasswordComplexity.COMPLEX,
                customTypes = setOf(PasswordCharacterType.DIGITS),
            ).resolve(),
        )
        assertEquals(
            PasswordRules(8, setOf(PasswordCharacterType.SYMBOLS)),
            PasswordGeneratorOptions(
                advanced = true,
                customComplexity = PasswordComplexity.COMPLEX,
                customLength = "8",
                customTypes = setOf(PasswordCharacterType.SYMBOLS),
            ).resolve(),
        )
    }

    @Test fun turningAdvancedOffRestoresThePresetAndTurningItOnRestoresCustomRules() {
        val options = PasswordGeneratorOptions(
            complexity = PasswordComplexity.SIMPLE,
            advanced = true,
            customLength = "64",
            customTypes = setOf(PasswordCharacterType.UPPERCASE),
        )

        assertEquals(PasswordRules(64, setOf(PasswordCharacterType.UPPERCASE)), options.resolve())
        assertEquals(
            PasswordRules(12, setOf(PasswordCharacterType.LOWERCASE, PasswordCharacterType.DIGITS)),
            options.copy(advanced = false).resolve(),
        )
        assertEquals(options.resolve(), options.copy(advanced = false).copy(advanced = true).resolve())
    }

    @Test fun customLengthAcceptsBothInclusiveBounds() {
        assertEquals(8, PasswordGeneratorOptions(advanced = true, customLength = "8").resolve().length)
        assertEquals(128, PasswordGeneratorOptions(advanced = true, customLength = "128").resolve().length)
    }

    @Test fun invalidCustomLengthsAreRejectedWithAnInlineMessage() {
        for (value in listOf("0", "7", "129", "-8", "+8", "8.0", "abc", "8a", " 12 ", "9999999999999999999999")) {
            val error = assertThrows("Invalid length: $value", IllegalArgumentException::class.java) {
                PasswordGeneratorOptions(advanced = true, customLength = value).resolve()
            }
            assertTrue(error.message.orEmpty().contains("8–128"))
        }
    }

    @Test fun rulesRejectAnEmptyAlphabetAndOutOfRangeLengthsBeforeGeneration() {
        assertThrows(IllegalArgumentException::class.java) { PasswordRules(20, emptySet()) }
        for (length in listOf(0, 7, 129, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                PasswordRules(length, setOf(PasswordCharacterType.DIGITS))
            }
        }
    }

    @Test fun generatedPasswordsMatchEveryNonEmptyTypeCombinationAndLengthBoundary() {
        val generator = PasswordGenerator()
        val types = PasswordCharacterType.entries
        for (mask in 1..15) {
            val selected = types.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            for (length in listOf(8, 20, 128)) {
                repeat(32) {
                    val password = generator.generate(PasswordRules(length, selected))

                    assertEquals(length, password.length)
                    assertTrue("Generated characters must belong to selected types", password.all { classify(it) in selected })
                    assertEquals("Every selected type must appear", selected, password.map(::classify).toSet())
                }
            }
        }
    }

    @Test fun eachPresetGeneratesItsResolvedRules() {
        val generator = PasswordGenerator()
        for (complexity in PasswordComplexity.entries) {
            val rules = PasswordGeneratorOptions(complexity = complexity).resolve()
            repeat(32) {
                val password = generator.generate(rules)
                assertEquals(rules.length, password.length)
                assertEquals(rules.types, password.map(::classify).toSet())
            }
        }
    }

    private fun classify(character: Char): PasswordCharacterType? = when (character) {
        in 'a'..'z' -> PasswordCharacterType.LOWERCASE
        in 'A'..'Z' -> PasswordCharacterType.UPPERCASE
        in '0'..'9' -> PasswordCharacterType.DIGITS
        in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~" -> PasswordCharacterType.SYMBOLS
        else -> null
    }
}

package org.jetbrains.fortran.ide.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.jetbrains.fortran.FortranLanguage

class FortranLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = FortranLanguage

    override fun getCodeSample(settingsType: LanguageCodeStyleSettingsProvider.SettingsType): String =
        when (settingsType) {
            SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE

            SettingsType.SPACING_SETTINGS -> SPACING_SAMPLE

            else /*SettingsType.BLANK_LINES_SETTINGS*/ -> BLANK_LINES_SAMPLE
        }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions(
                    "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                    "SPACE_AROUND_LOGICAL_OPERATORS",
                    "SPACE_AROUND_EQUALITY_OPERATORS",
                    "SPACE_AROUND_RELATIONAL_OPERATORS",
                    "SPACE_AROUND_ADDITIVE_OPERATORS",
                    "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                    "SPACE_AROUND_UNARY_OPERATOR",
                    "SPACE_AFTER_COMMA",
                    "SPACE_BEFORE_COMMA",
                    "SPACE_BEFORE_COLON",
                    "SPACE_AFTER_COLON"
                )
                consumer.renameStandardOption(
                    "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                    "Space around assigment operators (=, =>)"
                )
                consumer.renameStandardOption("SPACE_AROUND_LOGICAL_OPERATORS", "Logical operators (.or., .and.)")
                consumer.renameStandardOption("SPACE_AROUND_EQUALITY_OPERATORS", "Equality operators (==, .eq., ...)")
                consumer.renameStandardOption(
                    "SPACE_AROUND_RELATIONAL_OPERATORS",
                    "Relational operators (<, .lt., ...)"
                )
                consumer.renameStandardOption(
                    "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                    "Multiplicative operators (*, /)"
                )
                consumer.renameStandardOption("SPACE_AROUND_UNARY_OPERATOR", "Unary operators (+, -)")

                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_AROUND_NOT_OPERATOR", "Not operator (.not.)",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS
                )
                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_AROUND_POWER_OPERATOR", "Power operator (**)",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS
                )
                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java,
                    "SPACE_AROUND_EQUIVALENCE_OPERATOR",
                    "Equivalence operators (.eqv., .neqv.)",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS
                )
                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_AROUND_CONCAT_OPERATOR", "Concatenation operator (//)",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS
                )
                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_AROUND_DEFINED_OPERATOR", "Defined operators",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS
                )

                consumer.moveStandardOption(
                    "SPACE_BEFORE_COLON",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_OTHER
                )
                consumer.moveStandardOption(
                    "SPACE_AFTER_COLON",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_OTHER
                )

                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_BEFORE_DOUBLE_COLON", "Space before double-colon",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_OTHER
                )
                consumer.showCustomOption(
                    FortranCodeStyleSettings::class.java, "SPACE_AFTER_DOUBLE_COLON", "Space after double-colon",
                    CodeStyleSettingsCustomizableOptions.getInstance().SPACES_OTHER
                )
            }

            SettingsType.BLANK_LINES_SETTINGS -> {
                consumer.showStandardOptions(
                    "KEEP_BLANK_LINES_IN_CODE",
                    "KEEP_BLANK_LINES_IN_DECLARATIONS"
                )
                consumer.renameStandardOption("KEEP_BLANK_LINES_IN_DECLARATIONS", "Between subprograms")
            }

            else -> {
            }
        }
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions
    ) {
        commonSettings.KEEP_BLANK_LINES_IN_CODE = 1
        commonSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false
    }

    private val INDENT_SAMPLE: String by lazy {
        loadCodeSampleResource("codesamples/indents.f95")
    }

    private val BLANK_LINES_SAMPLE: String by lazy {
        loadCodeSampleResource("codesamples/blanklines.f95")
    }

    private val SPACING_SAMPLE: String by lazy {
        loadCodeSampleResource("codesamples/spacing.f95")
    }


    fun loadCodeSampleResource(resource: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(resource)
        return StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
    }
}
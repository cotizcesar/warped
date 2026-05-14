package com.warped.domain.highlighting

import dev.snipme.highlights.model.SyntaxLanguage
import timber.log.Timber
import javax.inject.Inject

class LanguageDetector @Inject constructor() {

    private val aliasMap: Map<String, String> = mapOf(
        "py" to "python", "python3" to "python", "py3" to "python",
        "js" to "javascript", "jsx" to "javascript", "mjs" to "javascript",
        "node" to "javascript", "ecmascript" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java",
        "c" to "c", "cpp" to "cpp", "c++" to "cpp", "cxx" to "cpp",
        "cc" to "cpp", "h" to "c", "hpp" to "cpp",
        "rs" to "rust", "rust" to "rust",
        "go" to "go", "golang" to "go",
        "sh" to "bash", "bash" to "bash", "shell" to "bash",
        "zsh" to "bash", "fish" to "bash",
        "swift" to "swift",
        "json" to "json", "jsonc" to "json",
        "yaml" to "yaml", "yml" to "yaml",
        "sql" to "sql", "mysql" to "sql", "psql" to "sql",
        "postgresql" to "sql", "sqlite" to "sql",
        "text" to "plaintext", "plaintext" to "plaintext",
        "txt" to "plaintext", "" to "plaintext",
    )

    private val LANGUAGE_KEYWORDS: Map<String, Set<String>> = mapOf(
        "python" to setOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "try", "except", "finally", "with", "as", "yield", "lambda", "pass", "raise", "self"),
        "javascript" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "try", "catch", "throw", "new", "this", "class", "export", "import", "async", "await", "typeof"),
        "typescript" to setOf("interface", "type", "enum", "namespace", "declare", "readonly", "as", "is", "keyof", "infer", "extends", "implements", "abstract", "public", "private", "protected", "static", "export", "import"),
        "kotlin" to setOf("fun", "val", "var", "class", "object", "interface", "data", "sealed", "when", "if", "else", "for", "while", "return", "suspend", "override", "private", "internal", "companion", "constructor"),
        "java" to setOf("public", "private", "protected", "static", "final", "class", "interface", "extends", "implements", "void", "int", "boolean", "String", "return", "if", "else", "for", "while", "new", "try"),
        "c" to setOf("int", "char", "float", "double", "void", "struct", "typedef", "sizeof", "return", "if", "else", "for", "while", "switch", "case", "break", "continue", "include", "define", "NULL"),
        "cpp" to setOf("class", "public", "private", "protected", "virtual", "override", "template", "typename", "namespace", "using", "std", "vector", "string", "auto", "constexpr", "include", "return", "if", "else", "for"),
        "rust" to setOf("fn", "let", "mut", "pub", "struct", "impl", "trait", "enum", "match", "if", "else", "for", "while", "loop", "return", "use", "mod", "self", "unsafe", "where"),
        "go" to setOf("func", "var", "const", "type", "struct", "interface", "package", "import", "return", "if", "else", "for", "range", "go", "defer", "chan", "select", "map", "make", "append"),
        "bash" to setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "return", "local", "export", "echo", "exit", "source", "readonly", "declare"),
        "swift" to setOf("func", "var", "let", "class", "struct", "enum", "protocol", "extension", "guard", "if", "else", "for", "while", "return", "import", "override", "private", "public", "mutating", "init"),
        "json" to setOf(),
        "yaml" to setOf(),
        "sql" to setOf("select", "from", "where", "insert", "update", "delete", "create", "table", "index", "join", "inner", "left", "right", "group", "order", "by", "having", "union", "limit", "offset"),
    )

    fun detect(fenceLabel: String?, code: String): String {
        if (!fenceLabel.isNullOrBlank()) {
            val normalized = fenceLabel.lowercase().trim()
            aliasMap[normalized]?.let { return it }
            Timber.w("LanguageDetector: unrecognized fence label '%s' — falling back to auto-detect", fenceLabel)
        }
        return autoDetect(code)
    }

    private fun autoDetect(code: String): String {
        val words = code.split(Regex("[\\s(){}\\[\\];:,.=<>!+\\-*/%&|^~?'\"`@#\$\\\\]+"))
            .map { it.lowercase() }
            .filter { it.length >= 2 }
            .toSet()
        val scores = LANGUAGE_KEYWORDS.map { (lang, keywords) ->
            lang to words.intersect(keywords).size
        }
        val best = scores.maxByOrNull { it.second } ?: return "plaintext"
        return if (best.second >= 2) best.first else "plaintext"
    }

    fun resolveSyntaxLanguage(language: String): SyntaxLanguage {
        return when (language) {
            "python" -> SyntaxLanguage.PYTHON
            "javascript" -> SyntaxLanguage.JAVASCRIPT
            "typescript" -> SyntaxLanguage.TYPESCRIPT
            "kotlin" -> SyntaxLanguage.KOTLIN
            "java" -> SyntaxLanguage.JAVA
            "c" -> SyntaxLanguage.C
            "cpp" -> SyntaxLanguage.CPP
            "rust" -> SyntaxLanguage.RUST
            "go" -> SyntaxLanguage.GO
            "bash" -> SyntaxLanguage.SHELL
            "swift" -> SyntaxLanguage.SWIFT
            "json", "yaml", "sql", "plaintext" -> SyntaxLanguage.DEFAULT
            else -> {
                Timber.w("LanguageDetector: no SyntaxLanguage mapping for '%s'", language)
                SyntaxLanguage.DEFAULT
            }
        }
    }
}

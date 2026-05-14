package com.warped.domain.highlighting

import com.google.common.truth.Truth.assertThat
import dev.snipme.highlights.model.SyntaxLanguage
import org.junit.jupiter.api.Test

class LanguageDetectorTest {

    private val detector = LanguageDetector()

    // === Alias Mapping Tests ===

    @Test
    fun `py fence label resolves to python`() {
        assertThat(detector.detect("py", "")).isEqualTo("python")
    }

    @Test
    fun `python3 fence label resolves to python`() {
        assertThat(detector.detect("python3", "")).isEqualTo("python")
    }

    @Test
    fun `py3 fence label resolves to python`() {
        assertThat(detector.detect("py3", "")).isEqualTo("python")
    }

    @Test
    fun `js fence label resolves to javascript`() {
        assertThat(detector.detect("js", "")).isEqualTo("javascript")
    }

    @Test
    fun `jsx fence label resolves to javascript`() {
        assertThat(detector.detect("jsx", "")).isEqualTo("javascript")
    }

    @Test
    fun `mjs fence label resolves to javascript`() {
        assertThat(detector.detect("mjs", "")).isEqualTo("javascript")
    }

    @Test
    fun `node fence label resolves to javascript`() {
        assertThat(detector.detect("node", "")).isEqualTo("javascript")
    }

    @Test
    fun `ecmascript fence label resolves to javascript`() {
        assertThat(detector.detect("ecmascript", "")).isEqualTo("javascript")
    }

    @Test
    fun `ts fence label resolves to typescript`() {
        assertThat(detector.detect("ts", "")).isEqualTo("typescript")
    }

    @Test
    fun `tsx fence label resolves to typescript`() {
        assertThat(detector.detect("tsx", "")).isEqualTo("typescript")
    }

    @Test
    fun `kt fence label resolves to kotlin`() {
        assertThat(detector.detect("kt", "")).isEqualTo("kotlin")
    }

    @Test
    fun `kts fence label resolves to kotlin`() {
        assertThat(detector.detect("kts", "")).isEqualTo("kotlin")
    }

    @Test
    fun `java fence label resolves to java`() {
        assertThat(detector.detect("java", "")).isEqualTo("java")
    }

    @Test
    fun `c fence label resolves to c`() {
        assertThat(detector.detect("c", "")).isEqualTo("c")
    }

    @Test
    fun `cpp fence label resolves to cpp`() {
        assertThat(detector.detect("cpp", "")).isEqualTo("cpp")
    }

    @Test
    fun `c++ fence label resolves to cpp`() {
        assertThat(detector.detect("c++", "")).isEqualTo("cpp")
    }

    @Test
    fun `cxx fence label resolves to cpp`() {
        assertThat(detector.detect("cxx", "")).isEqualTo("cpp")
    }

    @Test
    fun `cc fence label resolves to cpp`() {
        assertThat(detector.detect("cc", "")).isEqualTo("cpp")
    }

    @Test
    fun `h fence label resolves to c`() {
        assertThat(detector.detect("h", "")).isEqualTo("c")
    }

    @Test
    fun `hpp fence label resolves to cpp`() {
        assertThat(detector.detect("hpp", "")).isEqualTo("cpp")
    }

    @Test
    fun `rs fence label resolves to rust`() {
        assertThat(detector.detect("rs", "")).isEqualTo("rust")
    }

    @Test
    fun `rust fence label resolves to rust`() {
        assertThat(detector.detect("rust", "")).isEqualTo("rust")
    }

    @Test
    fun `go fence label resolves to go`() {
        assertThat(detector.detect("go", "")).isEqualTo("go")
    }

    @Test
    fun `golang fence label resolves to go`() {
        assertThat(detector.detect("golang", "")).isEqualTo("go")
    }

    @Test
    fun `sh fence label resolves to bash`() {
        assertThat(detector.detect("sh", "")).isEqualTo("bash")
    }

    @Test
    fun `bash fence label resolves to bash`() {
        assertThat(detector.detect("bash", "")).isEqualTo("bash")
    }

    @Test
    fun `shell fence label resolves to bash`() {
        assertThat(detector.detect("shell", "")).isEqualTo("bash")
    }

    @Test
    fun `zsh fence label resolves to bash`() {
        assertThat(detector.detect("zsh", "")).isEqualTo("bash")
    }

    @Test
    fun `fish fence label resolves to bash`() {
        assertThat(detector.detect("fish", "")).isEqualTo("bash")
    }

    @Test
    fun `swift fence label resolves to swift`() {
        assertThat(detector.detect("swift", "")).isEqualTo("swift")
    }

    @Test
    fun `json fence label resolves to json`() {
        assertThat(detector.detect("json", "")).isEqualTo("json")
    }

    @Test
    fun `jsonc fence label resolves to json`() {
        assertThat(detector.detect("jsonc", "")).isEqualTo("json")
    }

    @Test
    fun `yaml fence label resolves to yaml`() {
        assertThat(detector.detect("yaml", "")).isEqualTo("yaml")
    }

    @Test
    fun `yml fence label resolves to yaml`() {
        assertThat(detector.detect("yml", "")).isEqualTo("yaml")
    }

    @Test
    fun `sql fence label resolves to sql`() {
        assertThat(detector.detect("sql", "")).isEqualTo("sql")
    }

    @Test
    fun `mysql fence label resolves to sql`() {
        assertThat(detector.detect("mysql", "")).isEqualTo("sql")
    }

    @Test
    fun `psql fence label resolves to sql`() {
        assertThat(detector.detect("psql", "")).isEqualTo("sql")
    }

    @Test
    fun `postgresql fence label resolves to sql`() {
        assertThat(detector.detect("postgresql", "")).isEqualTo("sql")
    }

    @Test
    fun `sqlite fence label resolves to sql`() {
        assertThat(detector.detect("sqlite", "")).isEqualTo("sql")
    }

    @Test
    fun `text fence label resolves to plaintext`() {
        assertThat(detector.detect("text", "")).isEqualTo("plaintext")
    }

    @Test
    fun `plaintext fence label resolves to plaintext`() {
        assertThat(detector.detect("plaintext", "")).isEqualTo("plaintext")
    }

    @Test
    fun `txt fence label resolves to plaintext`() {
        assertThat(detector.detect("txt", "")).isEqualTo("plaintext")
    }

    @Test
    fun `empty string fence label falls back to auto detect`() {
        assertThat(detector.detect("", "")).isEqualTo("plaintext")
    }

    @Test
    fun `null fence label falls back to auto detect`() {
        assertThat(detector.detect(null, "")).isEqualTo("plaintext")
    }

    @Test
    fun `unrecognized fence label falls back to auto detect`() {
        assertThat(detector.detect("foobar", "")).isEqualTo("plaintext")
    }

    @Test
    fun `case insensitive alias matching lowercases input`() {
        assertThat(detector.detect("PY", "")).isEqualTo("python")
    }

    @Test
    fun `whitespace around alias label is trimmed`() {
        assertThat(detector.detect("  py  ", "")).isEqualTo("python")
    }

    // === Auto-Detection Tests ===

    @Test
    fun `auto detects python from def function`() {
        val code = "def hello():\n    return 'world'"
        assertThat(detector.detect(null, code)).isEqualTo("python")
    }

    @Test
    fun `auto detects python from import and class`() {
        val code = "import os\nclass MyClass:\n    pass"
        assertThat(detector.detect(null, code)).isEqualTo("python")
    }

    @Test
    fun `auto detects python from try except`() {
        val code = "try:\n    x = 1\nexcept Exception:\n    pass"
        assertThat(detector.detect(null, code)).isEqualTo("python")
    }

    @Test
    fun `auto detects javascript from function and const`() {
        val code = "function hello() {\n    const x = 42;\n    return x;\n}"
        assertThat(detector.detect(null, code)).isEqualTo("javascript")
    }

    @Test
    fun `auto detects javascript from let and async`() {
        val code = "async function fetch() {\n    let data = await api();\n    return data;\n}"
        assertThat(detector.detect(null, code)).isEqualTo("javascript")
    }

    @Test
    fun `auto detects kotlin from fun and val`() {
        val code = "fun main() {\n    val name = \"Kotlin\"\n    println(name)\n}"
        assertThat(detector.detect(null, code)).isEqualTo("kotlin")
    }

    @Test
    fun `auto detects java from public class`() {
        val code = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}"
        assertThat(detector.detect(null, code)).isEqualTo("java")
    }

    @Test
    fun `auto detects sql from select`() {
        val code = "SELECT * FROM users WHERE id = 1 ORDER BY name"
        assertThat(detector.detect(null, code)).isEqualTo("sql")
    }

    @Test
    fun `auto detects sql from insert and select`() {
        val code = "INSERT INTO users (name) VALUES ('Alice')\nSELECT * FROM users"
        assertThat(detector.detect(null, code)).isEqualTo("sql")
    }

    @Test
    fun `auto detects bash from echo and if`() {
        val code = "if [ -f file.txt ]; then\n    echo 'found'\nfi"
        assertThat(detector.detect(null, code)).isEqualTo("bash")
    }

    @Test
    fun `auto detects go from func and package`() {
        val code = "package main\n\nfunc main() {\n    fmt.Println(\"Hello\")\n}"
        assertThat(detector.detect(null, code)).isEqualTo("go")
    }

    @Test
    fun `auto detects rust from fn and let`() {
        val code = "fn main() {\n    let x = 42;\n    println!(\"{}\", x);\n}"
        assertThat(detector.detect(null, code)).isEqualTo("rust")
    }

    @Test
    fun `returns plaintext when no keywords match`() {
        val code = "this is just some random text with no programming keywords at all"
        assertThat(detector.detect(null, code)).isEqualTo("plaintext")
    }

    @Test
    fun `returns plaintext when only one keyword matches`() {
        val code = "function"
        assertThat(detector.detect(null, code)).isEqualTo("plaintext")
    }

    // === resolveSyntaxLanguage Tests ===

    @Test
    fun `resolveSyntaxLanguage python maps to PYTHON`() {
        assertThat(detector.resolveSyntaxLanguage("python")).isEqualTo(SyntaxLanguage.PYTHON)
    }

    @Test
    fun `resolveSyntaxLanguage javascript maps to JAVASCRIPT`() {
        assertThat(detector.resolveSyntaxLanguage("javascript")).isEqualTo(SyntaxLanguage.JAVASCRIPT)
    }

    @Test
    fun `resolveSyntaxLanguage typescript maps to TYPESCRIPT`() {
        assertThat(detector.resolveSyntaxLanguage("typescript")).isEqualTo(SyntaxLanguage.TYPESCRIPT)
    }

    @Test
    fun `resolveSyntaxLanguage kotlin maps to KOTLIN`() {
        assertThat(detector.resolveSyntaxLanguage("kotlin")).isEqualTo(SyntaxLanguage.KOTLIN)
    }

    @Test
    fun `resolveSyntaxLanguage java maps to JAVA`() {
        assertThat(detector.resolveSyntaxLanguage("java")).isEqualTo(SyntaxLanguage.JAVA)
    }

    @Test
    fun `resolveSyntaxLanguage c maps to C`() {
        assertThat(detector.resolveSyntaxLanguage("c")).isEqualTo(SyntaxLanguage.C)
    }

    @Test
    fun `resolveSyntaxLanguage cpp maps to CPP`() {
        assertThat(detector.resolveSyntaxLanguage("cpp")).isEqualTo(SyntaxLanguage.CPP)
    }

    @Test
    fun `resolveSyntaxLanguage rust maps to RUST`() {
        assertThat(detector.resolveSyntaxLanguage("rust")).isEqualTo(SyntaxLanguage.RUST)
    }

    @Test
    fun `resolveSyntaxLanguage go maps to GO`() {
        assertThat(detector.resolveSyntaxLanguage("go")).isEqualTo(SyntaxLanguage.GO)
    }

    @Test
    fun `resolveSyntaxLanguage bash maps to SHELL`() {
        assertThat(detector.resolveSyntaxLanguage("bash")).isEqualTo(SyntaxLanguage.SHELL)
    }

    @Test
    fun `resolveSyntaxLanguage swift maps to SWIFT`() {
        assertThat(detector.resolveSyntaxLanguage("swift")).isEqualTo(SyntaxLanguage.SWIFT)
    }

    @Test
    fun `resolveSyntaxLanguage json maps to DEFAULT`() {
        assertThat(detector.resolveSyntaxLanguage("json")).isEqualTo(SyntaxLanguage.DEFAULT)
    }

    @Test
    fun `resolveSyntaxLanguage yaml maps to DEFAULT`() {
        assertThat(detector.resolveSyntaxLanguage("yaml")).isEqualTo(SyntaxLanguage.DEFAULT)
    }

    @Test
    fun `resolveSyntaxLanguage sql maps to DEFAULT`() {
        assertThat(detector.resolveSyntaxLanguage("sql")).isEqualTo(SyntaxLanguage.DEFAULT)
    }

    @Test
    fun `resolveSyntaxLanguage plaintext maps to DEFAULT`() {
        assertThat(detector.resolveSyntaxLanguage("plaintext")).isEqualTo(SyntaxLanguage.DEFAULT)
    }

    @Test
    fun `resolveSyntaxLanguage unknown returns DEFAULT`() {
        assertThat(detector.resolveSyntaxLanguage("madeuplang")).isEqualTo(SyntaxLanguage.DEFAULT)
    }
}

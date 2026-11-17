package com.nexo.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the layers apart: core knows nothing above it, data never reaches up into domain or ui,
 * and ui talks to data only through repositories. Pure rules data also needs go in core/model;
 * rules only ui needs go in domain (an `XxxUseCase` when they read repositories); logic over one
 * repository stays in it; system holds Android entry points other than the activity.
 */
class ArchitectureTest {
    private val root: File = listOf("src/main/java", "app/src/main/java")
        .map(::File).first { it.isDirectory }

    private class Source(val path: String, val lines: List<String>) {
        val layer: String = path.substringBefore('/')
        val name: String = path.substringAfterLast('/')

        // Relative to com.nexo.app, with their line numbers.
        val appImports: List<Pair<Int, String>> = lines.mapIndexedNotNull { i, line ->
            line.removePrefix("import com.nexo.app.").takeIf { it != line }?.let { i + 1 to it }
        }
    }

    private val sources: List<Source> by lazy {
        val base = File(root, "com/nexo/app")
        base.walkTopDown().filter { it.isFile && it.extension == "kt" }.map {
            Source(it.relativeTo(base).invariantSeparatorsPath, it.readLines())
        }.toList()
    }

    // Allowed from any layer.
    private val everywhere = listOf("R", "BuildConfig")

    private val allowed = mapOf(
        "core" to listOf("core"),
        "data" to listOf("core", "data"),
        "domain" to listOf("core", "domain", "data.repository"),
        "ui" to listOf("core", "domain", "data.repository", "ui"),
        "system" to listOf("core", "data.repository", "system"),
    )

    @Test
    fun layersOnlyImportWhatTheyMay() {
        val violations = sources.filter { it.layer in allowed }.flatMap { source ->
            val ok = allowed.getValue(source.layer) + everywhere
            source.appImports.filterNot { (_, target) ->
                ok.any { target == it || target.startsWith("$it.") }
            }.map { (line, target) -> "${source.path}:$line imports $target" }
        }
        assertNoViolations(violations)
    }

    @Test
    fun viewModelsHoldNoContextOrComposeState() {
        val banned = listOf("android.content.Context", "android.widget.", "androidx.compose.")
        val violations = sources.filter { it.name.endsWith("ViewModel.kt") }.flatMap { source ->
            source.lines.mapIndexedNotNull { i, line ->
                val target = line.removePrefix("import ")
                if (line.startsWith("import ") && banned.any { target.startsWith(it) }) {
                    "${source.path}:${i + 1} imports $target"
                } else {
                    null
                }
            }
        }
        assertNoViolations(violations)
    }

    @Test
    fun modelsAndDomainArePlainKotlin() {
        val violations = sources.filter {
            it.path.startsWith("core/model/") || it.layer == "domain"
        }.flatMap { source ->
            source.lines.mapIndexedNotNull { i, line ->
                if (line.startsWith("import android.") || line.startsWith("import androidx.")) {
                    "${source.path}:${i + 1} ${line.removePrefix("import ")}"
                } else {
                    null
                }
            }
        }
        assertNoViolations(violations)
    }

    @Test
    fun domainReadsRepositoriesOnlyInUseCases() {
        val violations = sources.filter { source ->
            source.layer == "domain" && !source.name.endsWith("UseCase.kt") &&
                source.appImports.any { (_, target) -> target.startsWith("data.repository.") }
        }.map { it.path }
        assertNoViolations(violations)
    }

    @Test
    fun timeComesFromTheInjectedClock() {
        val reads = Regex(
            """\b(LocalDate|LocalDateTime|LocalTime|YearMonth|Instant|ZonedDateTime)\.now\(\)""" +
                """|System\.currentTimeMillis\(\)|ZoneId\.systemDefault\(\)|Clock\.system""",
        )
        val violations = sources.filter { it.name != "SystemZoneClock.kt" }.flatMap { source ->
            source.lines.mapIndexedNotNull { i, line ->
                reads.find(line)?.let { "${source.path}:${i + 1} reads ${it.value}" }
            }
        }
        assertNoViolations(violations)
    }

    // The ViewModel owns the coroutine, so work outlives the composable that asked for it.
    @Test
    fun viewModelsExposeNoSuspendFunctions() {
        val violations = sources.filter { it.name.endsWith("ViewModel.kt") }.flatMap { source ->
            source.lines.mapIndexedNotNull { i, line ->
                if (line.startsWith("    suspend fun ")) "${source.path}:${i + 1}" else null
            }
        }
        assertNoViolations(violations)
    }

    // Top-level `fun` declarations; the name is group 1.
    private val topLevelFun = Regex("""^(?:\w+ )*fun (?:<[^>]*> )?(?:[\w.]+\.)?(\w+)""")

    // Keeps every screen below a Route testable with plain state and lambdas.
    // The app root wires the back stack itself.
    @Test
    fun onlyRoutesReadTheNavigatorOrViewModels() {
        val reads = listOf("LocalNavigator.current", "hiltViewModel(", "hiltViewModel<")
        val violations = sources.filter { it.layer == "ui" && it.name != "NexoApp.kt" }
            .flatMap { source ->
                var function = ""
                source.lines.mapIndexedNotNull { i, line ->
                    topLevelFun.find(line)?.let { function = it.groupValues[1] }
                    if (reads.any { it in line } && !function.endsWith("Route")) {
                        "${source.path}:${i + 1} in $function"
                    } else {
                        null
                    }
                }
            }
        assertNoViolations(violations)
    }

    @Test
    fun onlyRoutesTakeAViewModel() {
        val viewModelParam = Regex("""\b\w+\s*:\s*\w*ViewModel\b""")
        val violations = sources.filter { it.layer == "ui" && it.name != "NexoApp.kt" }
            .flatMap { source ->
                composableSignatures(source).filter { (name, _, signature) ->
                    !name.endsWith("Route") && viewModelParam.containsMatchIn(signature)
                }.map { (name, line, _) -> "${source.path}:$line $name" }
            }
        assertNoViolations(violations)
    }

    // (name, line, parameter list text) for each top-level composable.
    private fun composableSignatures(source: Source): List<Triple<String, Int, String>> {
        val found = mutableListOf<Triple<String, Int, String>>()
        source.lines.forEachIndexed { i, line ->
            val name = topLevelFun.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
            val annotations = source.lines.subList(0, i).asReversed()
                .takeWhile { it.startsWith("@") }
            if (annotations.none { it.startsWith("@Composable") }) return@forEachIndexed
            // The parameter list runs until its parentheses balance again.
            val signature = StringBuilder()
            var depth = 0
            var opened = false
            for (next in source.lines.drop(i)) {
                signature.append(next).append('\n')
                depth += next.count { it == '(' } - next.count { it == ')' }
                if (next.contains('(')) opened = true
                if (opened && depth <= 0) break
            }
            found += Triple(name, i + 1, signature.toString())
        }
        return found
    }

    @Test
    fun everyFileWithARouteHasAPreview() {
        val violations = sources.filter { source ->
            source.lines.any { line ->
                !line.startsWith("private") &&
                    topLevelFun.find(line)?.groupValues?.get(1)?.endsWith("Route") == true
            } &&
                source.lines.none { "@Preview" in it }
        }.map { it.path }
        assertNoViolations(violations)
    }

    private fun assertNoViolations(violations: List<String>) =
        assertTrue(violations.joinToString("\n", prefix = "\n"), violations.isEmpty())
}

package com.nexo.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the layers apart: core knows nothing above it, data never reaches up into domain or ui,
 * and ui talks to data only through repositories. Nothing else enforces this in a one-module app.
 */
class ArchitectureTest {
    private val root: File = listOf("src/main/java", "app/src/main/java")
        .map(::File).first { it.isDirectory }

    private class Source(val path: String, val lines: List<String>) {
        val layer: String = path.substringBefore('/')
        val name: String = path.substringAfterLast('/')

        /** Imports of the app's own packages, relative to com.nexo.app, with their line numbers. */
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

    /** Resources and build constants come from the app module wherever they are needed. */
    private val everywhere = listOf("R", "BuildConfig")

    private val allowed = mapOf(
        "core" to listOf("core"),
        "data" to listOf("core", "data"),
        "domain" to listOf("core", "domain", "data.repository"),
        "ui" to listOf("core", "domain", "data.repository", "ui"),
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

    /** Top-level `fun` declarations; the name is group 1. */
    private val topLevelFun = Regex("""^(?:\w+ )*fun (?:<[^>]*> )?(?:[\w.]+\.)?(\w+)""")

    /**
     * Only a Route reaches for the navigator or a ViewModel, so every screen below it can be
     * previewed and tested with plain state and lambdas. The app root wires the back stack itself.
     */
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

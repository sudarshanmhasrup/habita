package dev.habita.server.data

import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.ProgramParser
import dev.habita.server.auth.sha256
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class LoadedProgram(val version: Long, val program: Program)

/**
 * Reads and parses the program file. Fails, and so stops the server, if the file is missing or
 * malformed. A changed file becomes a new program version; an unchanged one keeps its version.
 */
fun loadProgram(path: String, store: Store, now: Long, warn: (String) -> Unit = {}): LoadedProgram {
    val file = Path(path)
    check(file.exists()) { "Program file not found: ${file.toAbsolutePath()}" }
    val content = file.readText()
    val program = ProgramParser.parse(content)
    val version = store.programVersion(sha256(content), content, now)
    // Earlier plans, newest first, so an exercise dropped from the file keeps its history visible.
    val older = store.programs()
        .filter { it.version != version }
        .sortedByDescending { it.version }
        .mapNotNull { old ->
            runCatching { ProgramParser.parse(old.content) }
                .onFailure { warn("Program version ${old.version} no longer parses, so exercises only it had aren't served: ${it.message}") }
                .getOrNull()
        }
    return LoadedProgram(version, program.keepingRetired(older))
}
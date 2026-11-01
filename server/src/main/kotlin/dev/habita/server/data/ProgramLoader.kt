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
fun loadProgram(path: String, store: Store, now: Long): LoadedProgram {
    val file = Path(path)
    check(file.exists()) { "Program file not found: ${file.toAbsolutePath()}" }
    val content = file.readText()
    val program = ProgramParser.parse(content)
    return LoadedProgram(store.programVersion(sha256(content), content, now), program)
}
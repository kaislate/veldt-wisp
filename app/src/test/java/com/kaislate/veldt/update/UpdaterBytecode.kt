// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import java.io.File
import java.net.JarURLConnection

/**
 * The constant pools of the compiled `UpdateChecker`, as one searchable string.
 *
 * Every class, method and string literal a class file touches is named in its constant
 * pool, so scanning the pool shows what the checker can reach without running it. The
 * scan covers the synthetic `UpdateChecker$...` classes as well as the object itself,
 * because the body of each suspend function (`withContext { ... }`) compiles into one
 * of those: the object's own class file never mentions the network code at all.
 *
 * Shared by the two flavor tests, one asserting these names are present and the other
 * that they are absent, so that neither assertion can pass by looking in the wrong place.
 */
internal object UpdaterBytecode {

    /** What the GitHub checker must reference and the F-Droid one must not. */
    val UPDATER_MARKERS = listOf("java/net/", "api.github.com", "FileProvider", "package-archive")

    fun constantPools(): String {
        val url = UpdateChecker::class.java.getResource("UpdateChecker.class")
            ?: error("UpdateChecker.class not on the test classpath")
        val pools = when (url.protocol) {
            // AGP puts the app's classes on the unit-test classpath as a jar...
            "jar" -> {
                val jar = (url.openConnection() as JarURLConnection).jarFile
                val prefix = "com/kaislate/veldt/update/UpdateChecker"
                jar.entries().toList()
                    .filter { it.name == "$prefix.class" || it.name.startsWith("$prefix$") }
                    .map { entry -> jar.getInputStream(entry).use { it.readBytes() } }
            }
            // ...but a directory is just as valid a classpath entry, e.g. from an IDE run.
            "file" -> File(url.toURI()).parentFile
                .listFiles { f -> f.name == "UpdateChecker.class" || f.name.startsWith("UpdateChecker$") }
                .orEmpty()
                .map { it.readBytes() }
            else -> error("Unexpected classpath entry for UpdateChecker: $url")
        }
        return pools.joinToString("\n") { String(it, Charsets.ISO_8859_1) }
    }
}

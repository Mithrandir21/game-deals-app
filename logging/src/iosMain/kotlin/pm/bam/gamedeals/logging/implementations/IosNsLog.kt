package pm.bam.gamedeals.logging.implementations

import platform.Foundation.NSLog

/**
 * Kotlin/Native's `NSLog(format, vararg args)` binding doesn't bridge Kotlin
 * `String` to Objective-C `NSString *` cleanly through C variadic args, so
 * `NSLog("%@", line)` lands a garbage pointer at the variadic slot and
 * crashes with EXC_BAD_ACCESS. The safe pattern: call NSLog with a single
 * argument (the message itself becomes the format string) and pre-escape
 * `%` characters so application content can't be reinterpreted as format
 * specifiers.
 */
fun iosLog(line: String) {
    NSLog(line.replace("%", "%%"))
}

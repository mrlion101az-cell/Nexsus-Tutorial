package com.nexuscraft.nexustutorial;

/** What a step waits for. */
enum StepKind {
    /** Read it, then press Next. */
    INFO,
    /** The player runs a command. */
    COMMAND,
    /** The player opens a menu whose title contains some text. */
    OPEN,
    /** The player has some amount of a vanilla item. */
    HAVE
}

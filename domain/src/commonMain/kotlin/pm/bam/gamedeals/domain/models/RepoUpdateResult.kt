package pm.bam.gamedeals.domain.models

/** Outcome of a repository write call, used to react to auth-gated actions. */
enum class RepoUpdateResult {
    /** The remote and local state were successfully updated. */
    UPDATED,

    /** No-op: the user is logged out. Callers should prompt the user to sign in. */
    NOT_LOGGED_IN,
}

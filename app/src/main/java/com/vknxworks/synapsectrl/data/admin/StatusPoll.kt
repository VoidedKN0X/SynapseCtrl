package com.vknxworks.synapsectrl.data.admin

/**
 * Decides when following a background job on Synapse (purge history, delete room) stops. Synapse keeps
 * these statuses only in memory, for a while after the job ends, and loses them on restart: then every
 * check answers "not found" (or keeps failing), and polling would go on for as long as the dialog is open.
 */
class StatusPoll(private val maxFailures: Int = MAX_FAILURES) {
    enum class Next {
        /** Ask again after the usual wait. */
        CONTINUE,

        /** The job finished (complete or failed). */
        DONE,

        /** Synapse doesn't know the job any more, or it couldn't be checked several times in a row. */
        GONE,
    }

    /** Checks in a row that failed. */
    var failures = 0
        private set

    /** A status arrived. */
    fun onStatus(finished: Boolean): Next {
        failures = 0
        return if (finished) Next.DONE else Next.CONTINUE
    }

    /** The check failed; [notFound] when Synapse answered that it doesn't know the job. */
    fun onFailure(notFound: Boolean): Next {
        if (notFound) return Next.GONE
        failures++
        return if (failures >= maxFailures) Next.GONE else Next.CONTINUE
    }

    companion object {
        const val MAX_FAILURES = 5

        /** A job id Synapse gave back that can be followed (an empty one would ask for "status/"). */
        fun followable(id: String?): Boolean = !id.isNullOrBlank()
    }
}

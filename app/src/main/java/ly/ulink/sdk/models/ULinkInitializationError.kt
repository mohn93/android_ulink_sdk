package ly.ulink.sdk.models

/**
 * Exception thrown when SDK initialization fails
 * 
 * This error is thrown when essential operations during initialization fail,
 * such as bootstrap, deep link resolution, or deferred link checking.
 */
class ULinkInitializationError(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    companion object {
        /**
         * Creates an error for bootstrap failures
         */
        @JvmStatic
        @JvmOverloads
        fun bootstrapFailed(statusCode: Int, message: String, cause: Throwable? = null): ULinkInitializationError {
            return ULinkInitializationError(
                statusCode = statusCode,
                message = message,
                cause = cause
            )
        }

        /**
         * Creates an error for deep link resolution failures
         */
        @JvmStatic
        @JvmOverloads
        fun deepLinkResolutionFailed(statusCode: Int, message: String, cause: Throwable? = null): ULinkInitializationError {
            return ULinkInitializationError(
                statusCode = statusCode,
                message = "Deep link resolution failed: $message",
                cause = cause
            )
        }

        /**
         * Creates an error for deferred link check failures
         */
        @JvmStatic
        @JvmOverloads
        fun deferredLinkFailed(statusCode: Int, message: String, cause: Throwable? = null): ULinkInitializationError {
            return ULinkInitializationError(
                statusCode = statusCode,
                message = "Deferred link check failed: $message",
                cause = cause
            )
        }

        /**
         * Creates an error for last link data load failures
         */
        @JvmStatic
        @JvmOverloads
        fun lastLinkDataLoadFailed(message: String, cause: Throwable? = null): ULinkInitializationError {
            return ULinkInitializationError(
                statusCode = 0,
                message = "Failed to load last link data: $message",
                cause = cause
            )
        }
    }
}

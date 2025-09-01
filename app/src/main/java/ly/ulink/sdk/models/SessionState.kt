package ly.ulink.sdk.models

/**
 * Session states for tracking session lifecycle
 * 
 * This enum represents the different states a session can be in during its lifecycle.
 * It helps track the current status of session operations and provides better
 * state management for the ULink SDK.
 */
enum class SessionState {
    /**
     * No session operation in progress
     * This is the initial state and the state after a session has ended
     */
    IDLE,
    
    /**
     * Session start request sent, waiting for response
     * The SDK is currently attempting to start a new session
     */
    INITIALIZING,
    
    /**
     * Session successfully started and is currently active
     * The session is running and can be used for tracking
     */
    ACTIVE,
    
    /**
     * Session end request sent, waiting for response
     * The SDK is currently attempting to end the active session
     */
    ENDING,
    
    /**
     * Session start/end failed
     * An error occurred during session initialization or termination
     */
    FAILED
}
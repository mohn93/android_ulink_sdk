# Integration guide: initialization & network behavior

How to integrate the ULink Android SDK so your app stays responsive on slow or
intermittent networks. If you only read one thing:

> **Never block your UI on SDK initialization. Start it off your UI path, render
> your screen immediately, and react to links through the streams/listeners.**

## What initialization actually does

`ULink.initialize()` is not a local setup call — it performs a network "check-in"
with the backend (bootstrap) on **every launch**, which:

- opens a **session** (the unit of usage/analytics tracking),
- **refreshes** the installation token,
- updates **device/app metadata** (app version, OS, locale, network type),
- performs **reinstall detection**.

Because it is a network round-trip, it can be slow on a poor or congested
connection. That is exactly why it must not sit on your UI's critical path.

## The golden rule: don't block your UI

`initialize()` is a `suspend` function (Kotlin) / returns a `CompletableFuture`
(Java) precisely so it can run off the UI. Launch it fire-and-forget, render your
UI right away, and handle deep links asynchronously.

### Kotlin — correct

```kotlin
class MyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget. Do NOT await this before showing UI.
        appScope.launch {
            runCatching { ULink.initialize(this@MyApp, config) }
                .onFailure { Log.w("ULink", "init will retry: ${it.message}") }
        }
    }
}
```

Handle links wherever you route the user — the result is pushed to you:

```kotlin
lifecycleScope.launch {
    ULink.getInstance().dynamicLinkStream.collect { link -> route(link) }
}
// or, for unified links: ULink.getInstance().unifiedLinkStream.collect { ... }
```

### Java — correct

```java
// Fire-and-forget. Do NOT call .get() on the main thread.
ULink.initializeAsync(this, config);

// React to links via listeners:
ulink.setOnLinkListener(data -> route(data));
ulink.setOnUnifiedLinkListener(data -> route(data));
```

### Anti-patterns — do NOT do this

```kotlin
// Blocks the calling (often main) thread until a network round-trip completes.
runBlocking { ULink.initialize(this, config) }
```

- Showing a splash/spinner that only dismisses once `initialize()` returns.
- Awaiting `initializeAsync(...).get()` on the main thread.
- Gating your first screen on a link resolution completing.

All of these turn a slow bootstrap into a frozen app.

## Deferred deep links without a hang

Deferred deep linking (routing a brand-new install from a pre-install click) is
the one case where you genuinely want the resolved link before you route. Even
then, do not freeze on a spinner:

1. Render a sensible **default screen immediately**.
2. **Subscribe** to the link stream/listener and **re-route** when the deferred
   link arrives.
3. Apply your **own timeout** for the "waiting for a deferred link" state so a
   slow network can't strand the user on a loading screen.

## Transient failures are expected — and are not an outage

On some mobile carriers/regions, an individual bootstrap round-trip can stall or
fail even while the backend is up and reachable (packet loss / congestion on the
path). The SDK treats a bootstrap failure as **non-fatal** and retries
automatically:

- on the next app **foreground**, and
- as soon as the device **regains connectivity** (a default-network callback
  triggers an immediate retry).

A failed bootstrap does not disable the SDK; session start and link resolution
recover on the next successful check-in. **Do not** tear down the SDK, show a
hard error, or block the user because one bootstrap attempt failed.

## Checklist

- [ ] `initialize()` runs off the UI path (coroutine / `initializeAsync`) and is
      never awaited before rendering your first screen.
- [ ] Deep links are handled via `dynamicLinkStream` / `unifiedLinkStream` /
      `setOnLinkListener`, not by awaiting a return value.
- [ ] No `runBlocking { initialize() }` and no `.get()` on the main thread.
- [ ] Deferred-link UX shows a default screen and re-routes on arrival, with your
      own timeout.
- [ ] A bootstrap failure is treated as transient (the SDK retries), not fatal.

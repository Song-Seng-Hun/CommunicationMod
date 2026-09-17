# Protocol v2 core (not yet connected to gameplay)

UTF-8 JSON objects, one per line over stdin/stdout. Diagnostic text belongs on
stderr, never stdout. This module does not launch a game, disable Steam, filter
game observations, or implement any Downfall actions by itself.

Client first sends `{"type":"hello","protocol_version":2}`. The reply includes
the version, a fresh `session_id`, and capabilities. Legacy `Ready` and version 1
are rejected. A repeated handshake cannot reset consumed requests.

`{"type":"get_state"}` returns the current snapshot, or `STATE_UNAVAILABLE`.
Snapshots contain `runtime`, `observation`, `support`, `state_id`, `ready`, and
`actions`. Action descriptors contain `id`, `label`, and `parameters` (the
adapter's input constraints). Only a stable, supported snapshot offers actions.

To act, send an object with `type: "act"`, the exact `session_id`, `state_id`, a
nonempty unique `request_id`, the offered `action_id`, and `arguments: { ... }`.
Syntax is strict JSON: comments, unquoted/single-quoted keys, duplicate fields,
trailing values, and more than 64 nested containers are rejected.
An adapter validates all arguments against the descriptor and current UI before
execution. IDs are bounded to 128 characters and input lines to 1 MiB characters.
The production `DataReader` enforces a 1 MiB byte limit while reading, strict UTF-8,
and LF/CRLF framing; EOF terminates instead of spinning. Unterminated, malformed,
or oversized records close the reader;
the fixture server is only a trusted local probe, not that production transport.

The result acknowledges `applied` or `failed`; it is **not** the next stable state.
Before executing a callback, the request is consumed and readiness is cleared.
Even partially failing callbacks cannot be retried with the same request ID.
Unknown actions, invalid arguments, wrong sessions and stale states never execute.
No automatic retry is permitted after an execution failure. `get_state` then
returns a new state ID with the prior observation, `ready: false` and no actions
until a new stable snapshot is published. A state ID never denotes changed
snapshot contents. Raw exceptions go only to the local diagnostic sink.

The session retains up to 16,384 consumed request IDs; `SESSION_LIMIT` requires a
new session rather than evicting replay protection. Caller must serialize calls
on the game thread, invalidate on external/manual transitions, and publish only
player-visible data. No game-thread binding or transport replacement is enabled
in this milestone. Existing automation is held off by `AutomationSafety`.

Run `devtools/verify-protocol.ps1` for policy regression and actual JSON-lines
stdin/stdout roundtrip against `ProtocolFixtureServer`, with no game classpath.
This is not a real-game connection test. The fixture offers no actions.

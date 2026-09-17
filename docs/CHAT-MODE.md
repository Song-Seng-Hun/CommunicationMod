# Downfall Agent in ChatGPT Chat mode

The local Codex plugin and the ChatGPT connection are separate registrations. ChatGPT can retain a connection's tool catalog while its tunnel is offline. A listed tool is not proof of a working connection.

The existing six MCP tools run locally over stdio. Secure MCP Tunnel forwards requests to that same server. No public game port or HTTP server is needed. The entry point does not start a game when the tunnel or tool discovery starts; `sts_start_game` is the explicit game launch action.

## Preparation

From the CommunicationMod checkout, with PowerShell 7, Node 22+, and the previously prepared game runtime:

```powershell
.\devtools\chat-tunnel.ps1 -Operation Prepare
```

This downloads the Windows client from the official `openai/tunnel-client` latest release, verifies its published SHA-256 digest, builds/tests the existing MCP server, and checks the six tools and read-only game status through the actual MCP SDK. It writes only generated artifacts under ignored `target/chat-tunnel-tools`. The command can require outbound network and local process-query permission.

## Account connection

1. Sign in to [Platform tunnel settings](https://platform.openai.com/settings/organization/tunnels).
2. Create a dedicated Downfall tunnel associated with the ChatGPT workspace that should use it. Do not replace or reuse the llm_wiki tunnel target.
3. Obtain a runtime API key whose principal has Tunnels Read + Use; store it in a private file outside this repository. Do not paste keys into a chat, command argument, commit, or documentation. An admin key is not a runtime key.
4. Connect using the real tunnel ID and the key file:

```powershell
.\devtools\chat-tunnel.ps1 -Operation Connect -TunnelId 'tunnel_YOUR_ID' -KeyFile 'C:\private\downfall-runtime-key.txt'
```

`Connect` uses the client's native managed runtime under the fixed alias `communicationmod-downfall`. It supplies a key-file reference, not the secret itself, to the client. The profile directory is `%APPDATA%/tunnel-client/communicationmod`. Its standard MCP initialized notification is enabled. The stdio entry removes common OpenAI runtime/admin key environment variables before loading the game controller.

5. Check the returned `process_running`, `healthy`, and `ready` status. Successful profile creation or a running process alone does not prove readiness. After startup, inspect again if readiness is still pending:

```powershell
.\devtools\chat-tunnel.ps1 -Operation Status
```

6. In [ChatGPT Plugins](https://chatgpt.com/plugins), create a developer-mode connection named **Downfall Agent**, choose **Tunnel**, and select that tunnel. Review the discovered tools. Use a new Chat conversation and call `sts_game_status` first; then request game launch if wanted.

The six tools are `sts_game_status`, `sts_start_game`, `sts_get_state`, `sts_act`, `sts_get_context`, and `sts_get_request`. They preserve the existing state IDs, action receipts, visible-information policy, card descriptions and event acknowledgement rules. One controller should own the live game connection at a time; do not operate local Codex gameplay and Chat gameplay concurrently.

## Stop and update

```powershell
.\devtools\chat-tunnel.ps1 -Operation Stop
```

This stops only the managed Downfall tunnel. It does not remove the ChatGPT registration or stop the game. No OS startup task is installed. Reconnect after a reboot when you want Chat access.

After code changes, stop the tunnel, run `Prepare`, and reconnect with the same tunnel ID and key file. Refresh the ChatGPT connection's metadata when tools change. Generated client directories are versioned so preparation does not overwrite an executable in use.

## Verification on 2026-09-12

- Official Windows release `v0.0.14`; archive SHA-256 `784ab8da7b5a88f0109f1fd8aaf0a1c86067430b896dddf307ef7e3cc49fa1a5` matched the release API digest.
- All 11 existing MCP tests passed.
- `verify-chat-entry.mjs` started the actual stdio entry from an unrelated working directory, verified all six tool names, output schemas and annotations, and successfully called `sts_game_status` with the game stopped. Gameplay actions: zero.
- Platform tunnel settings redirected to login. No dedicated Downfall tunnel ID or runtime key was supplied. Therefore managed `Connect`/`Stop`, tunnel readiness, ChatGPT registration and end-to-end Chat tool calls remain unverified. Local preparation is complete; Chat mode is not yet connected.

Official references: [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels), [Connect and test](https://developers.openai.com/plugins/deploy/connect-chatgpt).

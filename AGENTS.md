# Repository Instructions

Estas instruções são lidas automaticamente pelo Claude Code (e por outros
agentes) como contexto de projeto. Escritas em português; código em inglês, UI
em português — mesma convenção do backend.

## Visão Geral

- **O que é:** app Android (Kotlin/Jetpack Compose) que captura notificações
  bancárias no aparelho, extrai valor/estabelecimento e envia como
  pré-lançamentos ("inbox") para uma instância do OpenMonetis via API.
- **Repo backend (par):** `Tavares-z/Control-Finances` (fork pessoal do
  OpenMonetis, Next.js/TypeScript, deploy Railway). É um **repositório
  separado**, com git e toolchain próprios — abrir em janela de VS Code
  dedicada. Mudança no contrato da API mexe nos dois repos.
- **Versão atual:** `versionName` 1.5.2 / `versionCode` 9. `minSdk` 31
  (Android 12), `compileSdk`/`targetSdk` 35. `applicationId`
  `br.com.openmonetis.companion`.
- **Stack:** Compose (Material 3) + Hilt (DI) + Room (persistência local) +
  Retrofit/OkHttp/Gson (rede) + WorkManager (sync em background).

## Arquitetura (mapa rápido)

- **Captura:** `CaptureNotificationListenerService` (NotificationListenerService)
  filtra por app monitorado + keyword de gatilho, faz dedup (mesmo app + mesmo
  valor em 3 min) e grava um `NotificationEntity` no Room. O parsing de
  valor/estabelecimento é 100% no cliente (`NotificationParser`, regex por
  banco — Nubank, Mercado Pago etc.).
- **Sync:** `SyncWorker` (WorkManager) envia lotes pendentes para
  `POST /api/inbox/batch` e concilia o resultado por `clientId`. É
  **event-driven**: cada notificação capturada enfileira um `OneTimeWork`. Há
  também um `PeriodicWork` de segurança (6h) que drena o que ficou pendente
  depois do backoff se esgotar — sem ele, notificações presas só reenviavam
  quando chegava uma nova.
- **UI:** telas em `ui/screens/` (Home, History, Settings, Setup, Logs), cada
  uma com seu ViewModel Hilt. Estado via `StateFlow` + `data class ...UiState`.
- **Segredos:** `SecureStorage` (EncryptedSharedPreferences) guarda serverUrl,
  token, expiresAt e flags. As notificações ficam no Room, **não** aqui —
  limpar credenciais (`clear()`) não apaga notificações pendentes.

## Autenticação — REGRAS (não presuma refresh)

- O backend emite **um único token opaco** (`opm_...`, hash SHA-256) com
  **validade de 1 ano**. **Não existe access+refresh token, nem rotação.** A
  criação está em `createApiTokenAction` no backend; a validade de 1 ano é
  cravada lá.
- **Não existe rota `/api/auth/device/refresh` no backend.** O app tem method,
  DTO e `updateTokens()` sobrando de um design que nunca foi implementado no
  servidor — chamar isso daria 404. Não construir fluxo de refresh sem antes
  criar a rota no backend (decisão maior, mexe no schema de auth).
- Endpoints usados: `GET /api/health` (público, sem auth), `POST
  /api/auth/device/verify` (valida token, devolve `expiresAt` ISO-8601),
  `POST /api/inbox/batch` (envio). O header é `Authorization: Bearer <token>`.
- **401 = token expirado ou revogado**, ponto final (não é transitório, com
  token de 1 ano). O tratamento correto é *reauth gracioso*: NÃO queimar as
  notificações em falha permanente — mantê-las `PENDING_SYNC`, setar a flag
  `SecureStorage.needsReauth`, pausar o sync e pedir novo token na UI (card na
  Home + uma única notificação). A flag é limpa em `saveCredentials()` num
  novo setup bem-sucedido, e o sync é re-disparado para drenar o acúmulo.

## Gotchas Android (aprendidos na prática)

- **OEMs matam o listener.** Xiaomi/MIUI, Samsung, Huawei etc. encerram o
  `NotificationListenerService` e atrasam WorkManager sem isenção de bateria —
  é a causa nº 1 de "parou de capturar". Há card na Home + item em Ajustes
  pedindo a isenção; preservar.
- **Notificação travada em "Enviando" (SYNCING).** Se o processo morre no meio
  do envio (Doze/OOM/crash), a notificação fica órfã em SYNCING e sai da fila.
  `SyncWorker.resetStaleSyncing()` recupera no início de cada run — seguro
  porque o unique work + REPLACE garantem um sync por vez.
- **`versionCode` e keystore.** Builds de debug (CI e local) usam uma keystore
  fixa versionada no repo, pra permitir atualizar o app instalado sem
  desinstalar. O self-update in-app baixa a release `debug-latest` do GitHub.
- **Expiração do token.** O aviso de "≤30 dias pra expirar" depende de
  `tokenExpiresAt` estar gravado. Gravar tanto no setup quanto ao verificar em
  Ajustes — senão o aviso fica cego logo após configurar.

## Regra de Verificação

Antes de afirmar que algo existe/não existe no app OU no backend, **ler o
arquivo real**. O erro clássico desta base foi assumir que havia refresh de
token porque o app tinha o client method — a rota não existia no backend. Se
uma mudança cruza os dois repos (contrato de API), confirmar nos dois lados
antes de implementar. Não compilar aqui não valida Kotlin — revisar imports e
símbolos manualmente, e deixar claro que o build real roda no ambiente Android
ou no CI.

## Release Process

Use semantic versioning for releases. Android releases must update both values in
`app/build.gradle.kts`:

- `versionName`: release version without a prefix, for example `1.5.2`
- `versionCode`: increment the previous integer value

Update `CHANGELOG.md` before publishing a release.

The final release commit must follow this exact naming convention:

```text
Release X.Y.Z
```

Example:

```text
Release 1.5.2
```

Create an annotated Git tag pointing to that final release commit:

```bash
git tag -a vX.Y.Z -m "Release X.Y.Z"
git push origin master
git push origin vX.Y.Z
```

Example:

```bash
git tag -a v1.5.2 -m "Release 1.5.2"
git push origin master
git push origin v1.5.2
```

The workflow `.github/workflows/build-release.yml` must be triggered only by
tags matching `v*` or manually through `workflow_dispatch`. Do not trigger
release workflows on regular pushes to `master`, otherwise GitHub Actions shows
duplicate release runs.

Before pushing a release tag, verify that it does not already exist locally or
remotely:

```bash
git tag --list "vX.Y.Z"
git ls-remote --tags origin "refs/tags/vX.Y.Z"
```

Do not move or force-update a published release tag unless explicitly required
to repair an incorrect release.

### Release signing keystore (secrets)

`build-release.yml` signs the release APK with a **release keystore** that lives
only as GitHub Actions secrets — it is **not** in the repo (only the debug
keystore is committed). Four secrets drive it:

- `KEYSTORE_BASE64` — the release `keystore.jks`, base64-encoded **on a single
  line, no line breaks** (`base64 -w 0`). A wrapped/multi-line value corrupts the
  file on decode and fails signing with
  `KeytoolException: ... Tag number over 30 is not supported`. This exact bug
  blocked every signed release until 2026-07-23.
- `KEYSTORE_PASSWORD` and `KEY_PASSWORD` — the store and key passwords. They are
  the **same value** (the keystore was generated with matching passwords).
- `KEY_ALIAS` — `openmonetis`.

The keystore was generated on 2026-07-23 with a one-shot CI workflow
(`gen-keystore.yml`, since removed) because there is no local JDK/keytool on the
dev machine — the app only ever builds in CI. If the keystore is ever lost, since
the app is **sideloaded** (not on the Play Store), generating a fresh one is
acceptable: existing installs just need a reinstall (Android rejects an update
signed by a different key). The passwords are stored in the maintainer's password
manager and are the only copy — losing them breaks all future signed releases.

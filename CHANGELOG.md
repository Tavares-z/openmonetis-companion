# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.5.2] - 2026-05-30

### Adicionado

- Links para os repositórios do Companion, do OpenMonetis e para o perfil do autor na seção Sobre
- Testes de regressão para extração de estabelecimentos em notificações do Cartão Mercado Pago

### Alterado

- Cards de notificações destacam a descrição normalizada e mantêm o texto original nos detalhes
- Card de permissão de captura aparece na tela inicial somente quando a permissão está desabilitada
- Resumo de apps monitorados na tela inicial abre os ajustes e destaca quando nenhum app está configurado
- Permissão para exibir alertas do Companion é solicitada somente ao ativar um alerta
- Seção Sobre consolidada para reduzir o espaço ocupado na tela de ajustes

### Corrigido

- Extração do estabelecimento em notificações achatadas do Cartão Mercado Pago, ignorando o texto informativo da próxima fatura
- Card de permissão da tela inicial agora abre corretamente as configurações nativas de captura de notificações

## [1.0.4] - 2026-02-16

### Alterado

- Projeto renomeado de **OpenSheets Companion** para **OpenMonetis Companion**
- Package Android: `br.com.opensheets.companion` → `br.com.openmonetis.companion`
- Classes renomeadas: `OpenSheetsApi` → `OpenMonetisApi`, `OpenSheetsApp` → `OpenMonetisApp`, `OpenSheetsCompanionTheme` → `OpenMonetisCompanionTheme`
- URLs do repositório atualizados para `openmonetis` / `openmonetis-companion`
- Database: `opensheets_companion.db` → `openmonetis_companion.db`
- SharedPreferences: `opensheets_secure_prefs` → `openmonetis_secure_prefs`
- README reescrito com novo nome e URLs

## [1.0.3] - 2026-02-15

### Corrigido

- Regex de extração do nome do estabelecimento nas notificações

## [1.0.2] - 2026-02-15

### Adicionado

- Logo na barra de título da tela principal
- Documentação completa no README

## [1.0.1] - 2026-02-14

### Corrigido

- Melhorias gerais de estabilidade

## [1.0.0] - 2026-02-14

### Adicionado

- Captura automática de notificações bancárias (Nubank, Itaú, Bradesco, etc.)
- Sincronização automática com OpenMonetis via API
- Setup guiado com QR Code para configuração de servidor e token
- Histórico de notificações com filtros por status
- Gatilhos de captura personalizáveis
- Tema claro/escuro (segue sistema)
- Retry automático via WorkManager
- Armazenamento seguro de token via EncryptedSharedPreferences

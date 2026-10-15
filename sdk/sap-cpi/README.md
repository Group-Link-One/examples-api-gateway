# SAP Integration Suite (CPI): assinatura GL-HMAC

Script Groovy para um iFlow do SAP Cloud Integration chamar o API Gateway com credencial secreta. O script lê a
credencial do Secure Store, calcula a assinatura `GL-HMAC` e preenche o header `Authorization`. O iFlow não
precisa de código próprio além deste script.

## Requisitos

- SAP Integration Suite, Cloud Integration (Neo ou Cloud Foundry).
- Uma credencial secreta do API Gateway (access key e secret key).
- Sem bibliotecas externas: o script usa apenas `javax.crypto` e a API pública do CPI.

## Passo a passo

### 1. Guardar a credencial no Secure Store

Em **Monitor > Integrations and APIs > Manage Security > Security Material**, crie um artefato do tipo
**User Credentials**:

| Campo    | Valor                              |
|----------|------------------------------------|
| Name     | `GL_API_GATEWAY` (ou outro alias)  |
| Kind     | Default                            |
| User     | access key (ex.: `sec_xxx`)        |
| Password | secret key                         |

### 2. Montar o iFlow

```
... → Content Modifier → Groovy Script (GLHmacSign.groovy) → Request Reply ──→ HTTP Receiver
```

**Content Modifier**, aba *Exchange Property*:

| Name                 | Source Type | Value                                   |
|----------------------|-------------|-----------------------------------------|
| `GL_CredentialAlias` | Constant    | `GL_API_GATEWAY`                        |
| `GL_Method`          | Constant    | `GET` (ou `POST`, `PUT`, `DELETE`)      |
| `GL_Path`            | Constant    | caminho da rota, ex.: `/v1/recurso`     |
| `GL_Query`           | Expression  | query string já codificada, sem `?`. Opcional |

**Groovy Script**: adicione o arquivo `GLHmacSign.groovy` sem alterações. A função é `processData`.

**HTTP Receiver adapter**:

| Campo          | Valor                                              |
|----------------|----------------------------------------------------|
| Address        | `https://api-gateway.grouplinkone.com${property.GL_Path}` |
| Query          | vazio (o script envia a query via `CamelHttpQuery`) |
| Method         | Dynamic, `${header.CamelHttpMethod}`               |
| Authentication | None                                               |

**Runtime Configuration** do iFlow: inclua `Authorization` em **Allowed Header(s)**. Sem isso, o CPI não envia o
header para o gateway.

### 3. Testar

Use `GL_Method = GET` e `GL_Path = /internal/verify`. A resposta `200` traz `"authenticated": true` e os escopos
da credencial.

## Cuidados

- **O que é assinado precisa ser exatamente o que sai.** O caminho de `GL_Path` precisa ser o mesmo do Address, e
  a query de `GL_Query` precisa estar codificada do mesmo jeito que vai na URL (ex.: espaço como `%20`). Não use o
  campo Query do adapter.
- **Corpo**: em `POST`/`PUT`, o script assina o corpo atual da mensagem. Não coloque nenhum passo que altere o
  corpo entre o script e o HTTP Receiver. Em `GET`, o script esvazia o corpo, porque o gateway assina um corpo vazio.
- **Relógio**: o timestamp vale por 5 minutos. A assinatura é gerada no momento da chamada, então retries do
  adapter devem passar pelo script de novo.

## Erros comuns

| Resposta                                   | Causa provável                                              |
|--------------------------------------------|-------------------------------------------------------------|
| `401 missing Authorization header`         | `Authorization` não está em Allowed Header(s)               |
| `401 unsupported auth scheme`              | Authentication do adapter não está como None                |
| `401 signature mismatch`                   | caminho, query ou corpo enviados diferem do que foi assinado, ou secret key errada |
| `401 timestamp skew too large`             | mensagem reprocessada depois de 5 minutos sem passar pelo script |
| `403`                                      | credencial sem o escopo da rota ou IP fora da allowlist     |
| Erro no script `Secure Store alias not found` | alias em `GL_CredentialAlias` não existe no Security Material |

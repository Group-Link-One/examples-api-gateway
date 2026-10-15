# Exemplos de SDK do API Gateway

Este repositório contém exemplos de SDK em múltiplas linguagens de programação, demonstrando como autenticar e interagir com o API Gateway. **Estes são exemplos educacionais** para ajudar você a entender como implementar a autenticação em sua própria aplicação.

> ⚠️ **Importante**: Estes exemplos foram criados para fins didáticos. Adapte-os às necessidades específicas do seu projeto antes de usar em produção.

## Linguagens Disponíveis

Cada pasta contém um exemplo completo e funcional:

- **[Python](sdk/python/)** - Usando a biblioteca `requests`
- **[Node.js](sdk/nodejs/)** - Usando módulos nativos `http` e `crypto`
- **[Java](sdk/java/)** - Usando bibliotecas padrão do Java
- **[C#](sdk/csharp/)** - Usando `HttpClient` do .NET
- **[Go](sdk/go/)** - Usando pacotes padrão `net/http` e `crypto`
- **[SAP Integration Suite (CPI)](sdk/sap-cpi/)** - Script Groovy para iFlow, com a credencial no Secure Store

## Como Funciona a Autenticação

O API Gateway utiliza o esquema de autenticação `GL-HMAC` com dois tipos de credenciais:

### Formato do Header de Autorização

Todas as requisições devem incluir o header `Authorization` no seguinte formato:

```
Authorization: GL-HMAC access_key=<chave_de_acesso> signature=<assinatura> timestamp=<timestamp>
```

### 1. Credenciais Públicas (Sem validação HMAC)

Para rotas que não exigem validação de assinatura HMAC, você ainda precisa enviar o header completo, mas a assinatura não será validada:

**Exemplo:**
```
Authorization: GL-HMAC access_key=pub_test_abc123 signature=public timestamp=1706304000
```

**Observação**: A palavra `public` é usada como placeholder para a assinatura, mas qualquer valor pode ser usado já que não será validado.

### 2. Credenciais Secretas (Com validação HMAC)

Para rotas que exigem validação de assinatura HMAC:

```
Authorization: GL-HMAC access_key=sec_test_xyz789 signature=<assinatura_calculada> timestamp=1706304000
```

A assinatura é calculada da seguinte forma:

```
string_para_assinar = "{MÉTODO}\n{CAMINHO}\n{QUERY}\n{BODY_SHA256}\n{TIMESTAMP}"
assinatura = HMAC-SHA256(chave_secreta, string_para_assinar)
```

**Componentes da assinatura:**
- `MÉTODO`: Método HTTP em maiúsculas (GET, POST, PUT, DELETE, etc.)
- `CAMINHO`: Caminho da requisição completo (ex: `/internal/verify`)
- `QUERY`: Query string da URL (ex: `param1=value1&param2=value2`), ou string vazia se não houver parâmetros
- `BODY_SHA256`: Hash SHA-256 do corpo da requisição em formato hexadecimal minúsculo
  - Para requisições sem corpo, use o hash de string vazia: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `TIMESTAMP`: Timestamp Unix em formato string (segundos desde 1970)

**Exemplo de cálculo:**
```python
# Para GET /internal/verify no timestamp 1706304000
método = "GET"
caminho = "/internal/verify"
query = ""  # sem query string
timestamp = "1706304000"
body_hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"  # hash de string vazia

string_para_assinar = f"{método}\n{caminho}\n{query}\n{body_hash}\n{timestamp}"
assinatura = hmac.new(chave_secreta.encode(), string_para_assinar.encode(), hashlib.sha256).hexdigest()

# Header final
auth_header = f"GL-HMAC access_key={access_key} signature={assinatura} timestamp={timestamp}"
```

**Exemplo com Query String:**
```python
# Para GET /internal/verify?test=1&debug=true no timestamp 1706304000
método = "GET"
caminho = "/internal/verify"
query = "test=1&debug=true"  # query string da URL
timestamp = "1706304000"
body_hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

string_para_assinar = f"{método}\n{caminho}\n{query}\n{body_hash}\n{timestamp}"
# ... resto do cálculo
```

**Exemplo com POST e corpo JSON:**
```python
import json
import hashlib

# Corpo da requisição
body = json.dumps({"message": "Hello", "timestamp": 1706304000})
body_bytes = body.encode('utf-8')

# Calcular hash do corpo
body_hash = hashlib.sha256(body_bytes).hexdigest()

# Para POST /internal/verify no timestamp 1706304000
método = "POST"
caminho = "/internal/verify"
query = ""
timestamp = "1706304000"

string_para_assinar = f"{método}\n{caminho}\n{query}\n{body_hash}\n{timestamp}"
assinatura = hmac.new(chave_secreta.encode(), string_para_assinar.encode(), hashlib.sha256).hexdigest()
```

## Estrutura dos Exemplos

Cada exemplo de SDK inclui:

- **README.md** - Documentação específica da linguagem
- **Arquivo principal** - Código de exemplo completo com:
  - Classe/função cliente para fazer requisições
  - Exemplos de uso com credenciais públicas e secretas
  - Implementação do algoritmo de assinatura HMAC
  - Configuração via variáveis de ambiente

## Executando os Exemplos

### Configuração Comum

Todos os SDKs usam as mesmas variáveis de ambiente:

```bash
# URL do API Gateway (padrão: https://api-gateway.grouplinkone.com/)
export GATEWAY_URL="http://localhost:8080"

# Credencial Pública (sem validação HMAC)
export PUBLIC_ACCESS_KEY="pub_test_abc123"

# Credencial Secreta (com validação HMAC)
export SECRET_ACCESS_KEY="sec_test_xyz789"
export SECRET_KEY="super_secret_key_12345"
```

### Endpoint de Teste

Os exemplos utilizam o endpoint `/internal/verify` que está disponível tanto para credenciais públicas quanto secretas:

- **GET /internal/verify** - Verifica a autenticação e retorna informações sobre a credencial
- **POST /internal/verify** - Aceita corpo JSON e verifica a autenticação

### Exemplos por Linguagem

#### Python

```bash
cd sdk/python
pip install -r requirements.txt
python api_gateway_client.py
```

O exemplo em Python demonstra:
- Como criar um cliente HTTP com `requests`
- Como calcular assinatura HMAC usando `hmac` e `hashlib`
- Como fazer requisições GET e POST com autenticação

#### Node.js

```bash
cd sdk/nodejs
node apiGatewayClient.js
```

O exemplo em Node.js demonstra:
- Como usar módulos nativos (sem dependências externas)
- Como construir requisições HTTP manualmente
- Como implementar HMAC com `crypto`

#### Java

```bash
cd sdk/java
javac APIGatewayClient.java
java APIGatewayClient
```

O exemplo em Java demonstra:
- Como usar `HttpURLConnection` para requisições HTTP
- Como calcular SHA-256 e HMAC-SHA256
- Como trabalhar com headers de autenticação

#### C#

```bash
cd sdk/csharp
dotnet run
```

O exemplo em C# demonstra:
- Como usar `HttpClient` do .NET
- Como implementar HMAC com `System.Security.Cryptography`
- Como estruturar um cliente reutilizável

#### Go

```bash
cd sdk/go
go run main.go
```

O exemplo em Go demonstra:
- Como usar pacotes padrão `net/http` e `crypto`
- Como implementar um cliente HTTP com HMAC
- Como trabalhar com tipos e interfaces do Go

### Tipos de Credenciais

#### Credencial Pública

**Uso**: Rotas que não exigem validação HMAC, para chamadas em endpoints públicos ou que não precisam de validação de integridade.

**Características**:
- Mais simples de implementar
- Não requer cálculo de assinatura HMAC
- Ainda precisa enviar o header no formato `GL-HMAC` mas a assinatura não é validada
- Use um valor placeholder como `"public"` para a assinatura

#### Credencial Secreta

**Uso**: Rotas que exigem validação de assinatura HMAC, para chamadas server-to-server onde a integridade e autenticidade são críticas.

**Características**:
- Requer cálculo correto da assinatura HMAC
- A assinatura inclui método, caminho, query string, hash do corpo e timestamp
- Mais seguro pois valida a integridade da requisição
- A chave secreta nunca é enviada na requisição

## Entendendo os Arquivos

### Estrutura do Repositório

```
sdk/
├── python/
│   ├── README.md                 # Documentação Python
│   ├── api_gateway_client.py     # Cliente + exemplo de uso
│   └── requirements.txt          # Dependências
├── nodejs/
│   ├── README.md                 # Documentação Node.js
│   └── apiGatewayClient.js       # Cliente + exemplo de uso
├── java/
│   ├── README.md                 # Documentação Java
│   └── APIGatewayClient.java     # Cliente + exemplo de uso
├── csharp/
│   ├── README.md                 # Documentação C#
│   ├── APIGatewayClient.csproj   # Arquivo de projeto
│   └── Program.cs                # Cliente + exemplo de uso
├── go/
│   ├── README.md                 # Documentação Go
│   ├── main.go                   # Exemplo de uso
│   ├── client.go                 # Implementação do cliente
│   └── go.mod                    # Módulo Go
├── sap-cpi/
│   ├── README.md                 # Configuração do iFlow no SAP CPI
│   └── GLHmacSign.groovy         # Script que assina a requisição
```

### Componentes de Cada SDK

Todos os SDKs implementam os mesmos conceitos:

1. **Cliente HTTP Base**
   - Configuração de URL base
   - Gerenciamento de headers
   - Tratamento de timeouts

2. **Autenticação**
   - Geração de timestamp Unix
   - Cálculo de hash SHA-256 do corpo
   - Criação de assinatura HMAC (quando necessário)
   - Montagem do header `Authorization` no formato `GL-HMAC`

3. **Métodos HTTP**
   - GET - Para consultas
   - POST - Para criação de recursos
   - Suporte a corpo JSON

4. **Exemplo de Uso**
   - Teste com credencial pública
   - Teste com credencial secreta
   - Requisições com e sem corpo

## Adaptando para Seu Projeto

Para usar estes exemplos no seu projeto:

1. **Copie o cliente da sua linguagem**
   - Adapte os métodos HTTP às suas necessidades
   - Adicione tratamento de erros específico
   - Implemente retry logic se necessário

2. **Configure suas credenciais**
   - Obtenha access_key e secret_key do seu API Gateway
   - Nunca commite credenciais no código
   - Use variáveis de ambiente ou gerenciador de secrets

3. **Ajuste para suas rotas**
   - Verifique quais rotas exigem validação HMAC
   - Adapte os caminhos das requisições
   - Implemente os endpoints específicos da sua API

4. **Adicione features de produção**
   - Logging estruturado
   - Métricas e observabilidade
   - Tratamento robusto de erros
   - Retry com backoff exponencial
   - Circuit breaker para resiliência

## Recursos Adicionais

- **Especificação da API**: Em produção para cada serviço.
- **Suporte**: [\[Link para suporte\]](https://grouplinkone.com/suporte-tecnico)

## Dúvidas Frequentes

**P: Posso usar estes exemplos em produção?**
R: Estes são exemplos educacionais. Adapte-os às necessidades do seu projeto, adicione tratamento de erros robusto, logging, e testes antes de usar em produção.

**P: Como obtenho minhas credenciais?**
R: Entre em contato com a equipe comercial.

**P: O que fazer se a assinatura HMAC estiver incorreta?**
R: Verifique:
- O método HTTP está em maiúsculas?
- O caminho da URL está correto?
- A query string está incluída corretamente (ou vazia se não houver)?
- O timestamp é o mesmo usado no header e na string para assinar?
- O hash do corpo está em hexadecimal minúsculo?
- A chave secreta está correta?
- O formato do header de autorização está correto: `GL-HMAC access_key=... signature=... timestamp=...`

**P: Qual a diferença entre credencial pública e secreta?**
R: Credenciais públicas não validam a assinatura HMAC (você pode usar qualquer valor como placeholder). Credenciais secretas validam a assinatura HMAC calculada a partir do método, caminho, query, corpo e timestamp, garantindo a integridade da requisição.

**P: Preciso incluir a query string na assinatura?**
R: Sim, a query string faz parte da string para assinar. Se não houver query string, use uma string vazia.

**P: Existe limite de requisições?**
R: Sim, máximo de 6 requests por segundo por padrão, por IP.

## Contribuindo

Entre em contato diretamente com a equipe comercial.

## Licença

MIT
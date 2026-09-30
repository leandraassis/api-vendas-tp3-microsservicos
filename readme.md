# API Vendas: Microsserviços com autenticação JWT

Sistema de vendas em microsserviços (Java 17, Spring Boot, Spring Cloud). Todo acesso passa por um
**API Gateway**, que só deixa chegar aos serviços quem apresentar um **token JWT** válido.

## 1. Arquitetura
| Serviço | Porta | Função |
|---|---|---|
| `gateway` | 8085 | Entrada única; valida o JWT e roteia para os serviços |
| `auth-service` | 8084 | Cadastro de usuários, login e refresh (emite os tokens) |
| `produtos-service` | 8081 | Consulta de produtos |
| `clientes-service` | 8083 | Listagem de clientes |
| `vendas-service` | 8082 | Registro de vendas (consulta o preço no `produtos-service`) |
| `eureka-server` | 8761 | Service Discovery (painel em http://localhost:8761) |
| `config-server` | 8888 | Configuração centralizada (`config-repo/`) |

- O **`auth-service` é independente**: tem banco próprio (H2 em memória) e não chama nenhum outro serviço.
  O gateway valida os tokens sozinho, usando o mesmo segredo, sem consultar o `auth-service`.
- Cada serviço tem seu próprio banco H2 em memória (os dados somem ao reiniciar). Produtos e clientes
  de exemplo são criados a cada inicialização.
- No Docker Compose, apenas o `gateway` (8085) e o `eureka-server` (8761) são publicados para fora; as demais
  portas da tabela são internas à rede do Docker.

## 2. Tecnologia de autenticação: JWT

Autenticação **stateless com JWT** (biblioteca JJWT 0.12.6, assinatura HMAC-SHA256).
O `auth-service` assina os tokens e o gateway confere a assinatura com o mesmo `jwt.secret`
(definido em `config-repo/auth-service.properties` e `gateway/src/main/resources/application.properties`;
os dois valores precisam ser iguais).

São emitidos **dois tokens** no login:

| Token | Validade | Uso |
|---|---|---|
| **access token** (`type=access`) | 60 s (`jwt.access-expiration`, em ms) | Enviado em `Authorization: Bearer` para acessar rotas protegidas |
| **refresh token** (`type=refresh`) | 7 dias (`jwt.refresh-expiration`, em ms) | Enviado só no corpo do `POST /auth/refresh` para obter um novo par |

O gateway **rejeita** o refresh token se ele for usado como Bearer, e o `/auth/refresh` rejeita um access token.
A validade de 60 s do access token é proposital, para permitir demonstrar o refresh sem esperar;
em produção use algo como 900000 (15 min).

Claims dos tokens: `sub` (e-mail), `type`, `iat`, `exp`, `jti`.

## 3. Como executar

Todas as chamadas devem ser feitas **pelo gateway**: `http://localhost:8085/{nome-do-serviço}/{rota}`.

### 3.1 Docker Compose

Requisito: Docker com Docker Compose.

```bash
docker compose up --build
```

O primeiro build baixa dependências e leva alguns minutos. Confira em http://localhost:8761 que aparecem
`AUTH-SERVICE`, `PRODUTOS-SERVICE`, `CLIENTES-SERVICE`, `VENDAS-SERVICE` e `GATEWAY`. O gateway atualiza sua
lista de serviços a cada ~30 s, então uma rota recém-registrada pode demorar um pouco para responder.

> O `docker-compose.yml` faz os serviços de negócio esperarem o `config-server` ficar *healthy* (servindo
> configuração) antes de iniciar; sem isso eles subiriam sem configuração (porta 8080, sem Eureka, sem `jwt.secret`).
> Por isso o `config-server` leva alguns segundos a mais para liberar os demais. Ao rodar `docker compose ps`,
> ele deve aparecer como `(healthy)`.

Para derrubar: `docker compose down`.

### 3.2 Local, sem Docker

Requisitos: **JDK 17** (o JDK 21 também funciona; o JDK 25 não compila o `auth-service`) e Maven 3.9+.
Em terminais separados, **nesta ordem**, dentro de cada pasta:

```bash
cd eureka-server    && mvn spring-boot:run     # 1º
cd config-server    && mvn spring-boot:run     # 2º
cd auth-service     && mvn spring-boot:run     # 3º
cd produtos-service && mvn spring-boot:run
cd clientes-service && mvn spring-boot:run
cd vendas-service   && mvn spring-boot:run
cd gateway          && mvn spring-boot:run     # por último
```

### 3.3 Kubernetes

Ver [`k8s/README.md`](k8s/README.md) (cluster `kind`, manifests em `k8s/`).

## 4. Como realizar a autenticação

1. **Cadastrar** um usuário (uma vez): `POST /auth-service/usuarios`
2. **Fazer login**: `POST /auth-service/auth/login` com e-mail e senha.
   A resposta traz o `accessToken` e o `refreshToken`.
3. **Chamar as rotas protegidas** enviando o access token no cabeçalho:
   `Authorization: Bearer <accessToken>`

Resposta do login (e do refresh):

```json
{
  "accessToken": "eyJh...",
  "refreshToken": "eyJh...",
  "tokenType": "Bearer",
  "expiresIn": 60
}
```

`expiresIn` é a validade do access token, em segundos.

## 5. Como usar o endpoint de refresh

Quando o access token expirar, as rotas protegidas passam a responder **401**. Nesse caso, envie o
refresh token no corpo de `POST /auth-service/auth/refresh` (não precisa do cabeçalho `Authorization`):

```json
{ "refreshToken": "<refreshToken recebido no login>" }
```

A resposta tem o mesmo formato do login, com **um novo `accessToken` e um novo `refreshToken`**.
Use o novo access token nas próximas chamadas. Refresh token inválido, expirado ou do tipo errado → **401**.

## 6. Endpoints

Caminhos considerados a partir do gateway (`http://localhost:8085`).

### Públicos (não exigem token)

| Método | Caminho | Descrição | Respostas |
|---|---|---|---|
| POST | `/auth-service/usuarios` | Cadastra usuário `{nome, email, senha}` | 201 (id) · 409 e-mail já cadastrado |
| POST | `/auth-service/auth/login` | Login `{email, senha}` | 200 (tokens) · 401 credenciais inválidas |
| POST | `/auth-service/auth/refresh` | Renova tokens `{refreshToken}` | 200 (tokens) · 401 token inválido |

### Protegidos (exigem `Authorization: Bearer <accessToken>`)

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/produtos-service/produtos` | Lista produtos |
| GET | `/produtos-service/produtos/{id}` | Busca um produto (404 se não existir) |
| GET | `/clientes-service/clientes` | Lista clientes |
| POST | `/vendas-service/vendas` | Registra venda `{idProduto, quantidade}` |
| GET | `/vendas-service/vendas` | Rota de teste do serviço de vendas (retorna um texto fixo) |

Qualquer outra rota do gateway também exige token. Sem token, com token inválido/adulterado, expirado,
ou com um refresh token no lugar do access token → **401**.

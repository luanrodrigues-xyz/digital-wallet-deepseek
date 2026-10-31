# Testando a API

Com o PostgreSQL e a aplicação rodando, a API responde em:

```text
http://localhost:8080/api/v1
```

## Pelo Swagger

Abra http://localhost:8080/swagger-ui.html

Escolha um endpoint, clique em **Try it out**, preencha os campos e depois em **Execute**. Ali você consegue ver o status HTTP e o corpo da resposta.

Também dá para usar o [Postman](https://www.postman.com/) ou o terminal com `curl`.

## 1. Criar uma carteira

`POST /api/v1/wallets`

JSON:

```json
{
  "holderName": "Carlos Silva"
}
```

No terminal:

```bash
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"holderName":"Carlos Silva"}'
```

A API deve responder com `201 Created`. Copie o `id` da carteira retornada.

## 2. Fazer um depósito

`POST /api/v1/wallets/{walletId}/deposits`

Substitua `SEU_WALLET_ID` pelo ID criado acima:

```bash
curl -X POST http://localhost:8080/api/v1/wallets/SEU_WALLET_ID/deposits \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: deposito-001" \
  -d '{"amount":100.00}'
```

Depósitos, saques e transferências exigem o header `Idempotency-Key`. Use uma chave nova para cada operação diferente. Se repetir a mesma chave com a mesma requisição, observe o que acontece.

No Swagger também precisa preencher esse header.

## 3. Consultar a carteira

```bash
curl http://localhost:8080/api/v1/wallets/SEU_WALLET_ID
```

Outras rotas que você pode experimentar:

| Método | Rota | O que faz |
| --- | --- | --- |
| GET | `/api/v1/wallets` | Lista carteiras |
| GET | `/api/v1/wallets/{walletId}/movements` | Consulta movimentações |
| POST | `/api/v1/wallets/{walletId}/withdrawals` | Saque |
| POST | `/api/v1/transfers` | Transferência |
| PUT | `/api/v1/wallets/{walletId}` | Altera o nome do titular |
| DELETE | `/api/v1/wallets/{walletId}` | Desativa a carteira |

Os formatos dos JSONs estão no Swagger. Experimente também entradas inválidas e compare as respostas com o comportamento esperado.

## Rodando os testes automatizados

```bash
mvn test
```

Alguns testes usam Testcontainers, então o Docker precisa estar funcionando.

Se encontrar um comportamento estranho, procure uma [issue aberta](https://github.com/luanrodrigues-xyz/digital-wallet-deepseek/issues). O código inicial pode ter problemas e isso faz parte do estudo.

[Voltar ao README](../README.md)

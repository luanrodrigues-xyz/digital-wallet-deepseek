# Como contribuir

Vou abrir issues aos poucos com problemas encontrados no código gerado pelo DeepSeek. A proposta é que quem está estudando Java e Spring Boot consiga investigar e corrigir.

Não precisa ter experiência profissional. Pode começar por uma issue pequena.

## Pegando um desafio

1. Abra a [lista de issues](https://github.com/luanrodrigues-xyz/digital-wallet-deepseek/issues).
2. Escolha um problema e comente que pretende trabalhar nele, para evitar trabalho duplicado.
3. Tente reproduzir o comportamento descrito.
4. Entenda por que acontece antes de alterar o código.
5. Corrija e rode os testes. Se fizer sentido, adicione um teste para o problema.
6. Abra um Pull Request explicando o que mudou.

## Regras

**Não use IA para gerar código, testes ou soluções dos desafios.** O código inicial já foi gerado por IA. Agora o objetivo é estudar e resolver por conta própria.

Pode ler a documentação, pesquisar, consultar livros e fazer perguntas. Não precisa acertar de primeira.

Mantenha o PR focado no problema escolhido. Evite misturar correções que não têm relação com a issue.

## Branch e commit

Crie uma branch a partir da `main`, usando um nome que ajude a identificar a correção:

```bash
git checkout main
git pull
git checkout -b fix/nome-do-problema
```

Faça commits com mensagens simples, descrevendo o que mudou. Não precisa de texto elaborado nem de assinaturas ou menções a IA.

Antes do PR, rode:

```bash
mvn test
```

No Pull Request, conte qual era o problema, como você resolveu e o que testou. Se alguma coisa ficou pendente, avise.

## Ficou com dúvida?

Pode perguntar na própria issue. Inclusive se não entendeu o motivo do erro ou não sabe por onde começar.

A gente pode conversar sobre o comportamento, regras de negócio, segurança e caminhos para investigar. A ideia não é entregar a resposta pronta.

[Voltar ao README](../README.md)

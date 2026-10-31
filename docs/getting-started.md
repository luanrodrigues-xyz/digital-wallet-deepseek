# Rodando o projeto

Aqui tem o básico para baixar e executar a API na sua máquina.

## O que instalar

- [Git](https://git-scm.com/) para clonar o repositório.
- [Java JDK 21](https://adoptium.net/) para rodar o Spring Boot.
- [Maven](https://maven.apache.org/) para compilar e executar o projeto.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (ou Docker Engine com Compose) para subir o PostgreSQL.
- Uma IDE: [IntelliJ IDEA](https://www.jetbrains.com/idea/) **ou** [VS Code](https://code.visualstudio.com/). Não precisa das duas.

Se usar VS Code, instale as extensões *Extension Pack for Java* e *Spring Boot Extension Pack*.

[Postman](https://www.postman.com/) e [DBeaver](https://dbeaver.io/) são opcionais. O Postman ajuda a fazer requisições e o DBeaver a olhar o banco.

Antes de começar, confira no terminal:

```bash
java -version
mvn -version
docker compose version
git --version
```

O Java precisa estar na versão 21. Se o Maven não reconhecer o Java, confira a configuração do `JAVA_HOME`.

## 1. Baixar o projeto

```bash
git clone https://github.com/luanrodrigues-xyz/digital-wallet-deepseek.git
cd digital-wallet-deepseek
```

Abra essa pasta na IDE que você escolheu.

## 2. Subir o banco

Deixe o Docker rodando e execute:

```bash
docker compose up -d
docker compose ps
```

O `compose.yaml` sobe um PostgreSQL 16 na porta `5432`. Os dados locais são:

```text
Host: localhost
Porta: 5432
Banco: wallet
Usuário: wallet
Senha: wallet
```

Essas credenciais são apenas para o ambiente de estudo.

Se já tiver outro PostgreSQL usando a porta 5432, vai precisar liberar essa porta ou ajustar a configuração do container e da aplicação.

## 3. Iniciar a API

Na pasta do projeto:

```bash
mvn spring-boot:run
```

A aplicação usa a porta `8080` por padrão. O Flyway aplica as migrations quando ela inicia.

Abra o Swagger para conferir se a API está respondendo:

http://localhost:8080/swagger-ui.html

O próximo passo está em [Testando a API](api-testing.md).

## Para parar

Pare a aplicação no terminal com `Ctrl+C`. Para parar o banco:

```bash
docker compose down
```

Esse comando mantém os dados do volume. Não use `docker compose down -v` sem querer: o `-v` também apaga os dados do banco desse ambiente.

## Se não funcionar

- Veja se o Docker está ligado e se o container está de pé com `docker compose ps`.
- Confira se as portas `5432` e `8080` estão livres.
- Confira se `java -version` mostra a versão 21.
- Leia o erro no terminal. Se ficar travado e não souber resolver, pode perguntar em uma issue.

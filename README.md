# Plurali

App Android pessoal para sistemas plurais (substituto do SimplyPlural, que foi descontinuado).
Kotlin + Jetpack Compose. Os dados ficam só no aparelho (`files/data.json`, mais uma cópia `data.bak.json`).

## Funções
- **Front**: co-front, trocar de front, status personalizado, custom fronts, limpar front
- **Membros**: foto, pronomes, papel, cor, descrição, aniversário, campos personalizados, arquivar
- **Grupos** com subgrupos (subsistemas)
- **Histórico** de front: dá para editar, adicionar manualmente e filtrar
- **Estatísticas** de tempo de front por período
- **Quadro de mensagens** entre membros (com status lida/não lida) e **notas** por membro
- **Enquetes** com voto por membro, abstenção/veto e comentários
- **Backup**: exporta/importa um arquivo JSON (fotos incluídas) e **backup automático** diário/semanal numa pasta escolhida
- **Backup na nuvem**: cópia automática (a cada 1h/6h/24h, só no Wi‑Fi se quiser) no Firebase do Plurali, criptografada de ponta a ponta com um código de 20 letras. Restaura em outro celular com o código; guarda as 2 últimas cópias
- **Temas por perfil**: Clássico, Gatos, Jardim, Homem-Aranha, Coelhos, Inferno, Fadas, Exploração, Insetos, Desenho, Pássaros, Ladybug e Vento. Cada um muda cores, formato dos cartões e das fotos, contornos, enfeites e desenhos do fundo. Dá para ajustar cor, letras, tamanho, imagem de fundo e animação. A tela de login e alters novos usam o tema padrão; o tema da pessoa carrega ao entrar na conta
- **Logins**: Sistema (admin, com código de recuperação), um por membro, e visitante sem login
- **Conversas** diretas e em grupo (sem apagar; edição só no 1º minuto), com reações, fotos, fixadas e "visto"
- **Mural de avisos**, **wishlist**, **relações** entre membros e **mapa do sistema**
- **Corpo**: dados gerais + dados sensíveis criptografados com senha própria
- **Calendário** com filtro por pessoa/papel, aniversários e lembretes
- **Lembretes** de horário (alarme exato) e **timers de front**; enquetes com prazo que fecham sozinhas
- **Notificação fixa** de front com botões de troca, **widget**, **widgets de foto 1x1 e 2x2** (só a foto de quem está no front; tocar abre um menu para trocar, somar ou tirar alguém) e **atalho nas configurações rápidas**
- **Plano de segurança** acessível sem login e **"Sou novo aqui"** para novos alters
- **Tela de entrada simples**: Login do Sistema · Entrar sem login · Entrar com meu perfil (a lista de perfis só aparece nessa última, com bolinha de mensagens não lidas)
- **Mural de boas-vindas em grade**: textos e fotos (planta da casa, família…) lado a lado, com tamanho ajustável e foto em tela cheia com zoom
- **Texto formatado** no mural de boas-vindas e no mural de avisos: negrito, itálico, sublinhado, riscado, cor da letra, marca-texto, letra grande/pequena e alinhamento
- **Ouvir em voz alta**: cada caixa de texto do mural de boas-vindas tem um botão de ouvir, e há "Ouvir tudo". Dá para **gravar a própria voz** lendo cada item; sem gravação, o celular lê o texto com a voz automática
- **O que eu perdi?**: ao entrar com o perfil, mostra o que aconteceu desde a última vez (front, avisos, conversas, diário, tarefas, remédios, recados de companions), sem repetir o que a própria pessoa fez
- **Recados para quem entrar no front**: aparecem no início e chegam como notificação quando a pessoa entra
- **Remédios** com horários, notificação com botão "Tomei", registro de quem tomou/pulou e aviso de dose já tomada
- **Tarefas compartilhadas** com responsável, prazo e repetição (todo dia/semana/mês), registrando quem fez
- **Diário do sistema** com fotos e texto formatado
- **Mensagens de voz** nas conversas
- Campos com **visibilidade** (todos / só membros / privado), **busca** geral

## Plurali Companion (app para pessoas de fora do sistema)
Projeto `companion/` (APK `PluraliCompanion.apk`). Quem acompanha o sistema vê só o que o perfil dela permite
(front, mural, calendário, perfil completo de cada membro — capa, foto, papéis, apresentação, como acalmar, gostos, aniversário, campos, grupos, relações, wishlist, próximos compromissos e tempo de front —, histórico, plano de segurança, dados gerais do corpo)
e recebe aviso quando o front muda (o app confere a cada ~15 min).
O Companion usa o **tema de quem está no front** (as mesmas cores, formatos e enfeites escolhidos no Plurali) e, se o perfil permitir,
pode **mandar recados para o sistema**, que chegam no Plurali com notificação.

- Os dados vão para o Firebase Realtime Database **criptografados de ponta a ponta** (AES-GCM, uma chave por companion,
  que só existe no Plurali e no convite/QR). O Google só guarda bytes embaralhados.
- Cada companion tem um caminho aleatório `shares/<id>`; "Gerar novo convite" troca caminho e chave (corta o acesso);
  excluir o companion apaga os dados dele do Firebase.
- Membros escondidos que entram no front aparecem como "Alguém do sistema". Campos privados e dados sensíveis nunca são enviados.

O app já vem ligado ao projeto Firebase **plurali-8ffe7** (`FirebaseProject` em `Model.kt`): ninguém precisa configurar nada.
Os passos abaixo só servem se um dia for preciso recriar o projeto.

### Criar o Firebase (uma vez)
1. https://console.firebase.google.com → criar projeto (Analytics pode ficar desligado).
2. Authentication → Começar → Método de login → **Anônimo** → ativar.
3. Realtime Database → Criar banco de dados → local → **modo bloqueado**.
4. Aba **Regras**: colar as regras abaixo (o app também tem o botão "Copiar regras do banco") e publicar.
5. No Plurali (como Sistema): menu → Companions → colar o endereço do banco e a **Chave de API da Web**
   (Configurações do projeto → Geral) → "Testar e salvar".

```json
{
  "rules": {
    "shares": {
      "$id": {
        ".read": "auth != null",
        ".write": "auth != null && (!data.exists() || data.child('owner').val() === auth.uid)",
        ".validate": "!newData.exists() || newData.child('owner').val() === auth.uid"
      }
    }
  }
}
```

## Compilar
```powershell
$env:JAVA_HOME = (Get-ChildItem "$env:USERPROFILE\.android-build\jdk" -Directory)[0].FullName
.\gradlew.bat :app:assembleRelease :companion:assembleRelease
```
Os APKs ficam em `app\build\outputs\apk\release\app-release.apk` e `companion\build\outputs\apk\release\companion-release.apk`.

O APK é assinado com a chave de debug deste PC (`%USERPROFILE%\.android\debug.keystore`).
Guarde esse arquivo: só APKs assinados com a mesma chave instalam por cima da versão atual sem apagar os dados.

# FitAI - Personal Trainer & Nutricionista com Inteligência Artificial

O **FitAI** é um aplicativo Android nativo, moderno e de alta performance desenvolvido em **Kotlin** e **Jetpack Compose**, projetado para atuar como o personal trainer e nutricionista digital definitivo para usuários reais.

O app combina o poder da **Google Gemini API**, visão computacional com **ML Kit** e **CameraX**, persistência local ultra-rápida com **Room**, e sincronização em nuvem segura em tempo real com **Firebase Auth** e **Cloud Firestore**.

---

## 🌟 Principais Funcionalidades

### 1. Treinos Diários & Fichas Personalizadas
- **Divisão Inteligente:** Suporte dinâmico a 3, 4, 5 ou 6 dias de treino por semana (Fichas A, B, C, D, E, F), gerando fichas completas com **7 exercícios estruturados** para cada dia.
- **Edição & Renomeação de Fichas:** O usuário pode editar exercícios e **personalizar o nome de qualquer ficha** (ex: mudar "Treino A" para "Peito & Tríceps" ou "Braços"). A alteração é persistida localmente (Room + SharedPreferences por UID) e sincronizada no Firestore.
- **Instruções Detalhadas & Mapa Muscular:** Cada exercício contém biomecânica detalhada, postura correta, respiração e mapa muscular anatômico interativo.
- **Cronômetro de Descanso Global:** Timer persistente no rodapé com alerta sonoro e vibratório configurável entre as séries.
- **Gerenciamento de Séries e Cargas:** Registro de pesos, repetições, histórico de progressão de carga e status de conclusão por série.

### 2. Nutrição, Macros & Diário Alimentar
- **Cálculo Metabólico Preciso:** Metas diárias de calorias, proteínas, carboidratos e gorduras calculadas de acordo com o objetivo (Hipertrofia, Emagrecimento, Definição ou Saúde).
- **Scanner de Código de Barras:** Leitura instantânea de alimentos industrializados via **CameraX** e **Google ML Kit Barcode Scanning**.
- **Estimativa Calórica por Foto (IA Gemini Vision):** Análise de pratos e refeições diretamente pela câmera ou galeria, identificando alimentos, porções estimadas em gramas e macronutrientes automaticamente.
- **Alimentos Favoritos & Recentes:** Busca rápida e salvamento de alimentos frequentes por usuário.

### 3. Hidratação Inteligente
- **Meta Baseada em Peso Corporal:** Cálculo automático de recomendação de ingestão hídrica diária (ml por kg).
- **Adição Rápida:** Botões de incremento rápido (+200ml, +300ml, +500ml) e edição do total consumido no dia.
- **Lembretes Periódicos:** Notificações programadas via **WorkManager** (`ExistingPeriodicWorkPolicy.KEEP`) para garantir que o usuário atinja a meta.

### 4. Suplementação com Alarmes Pontuais
- **Cadastro Personalizado:** Registro de suplementos (Whey, Creatina, Multivitamínico, etc.), doses e horários específicos.
- **Lembretes Exatos no Fuso Local:** Agendamento confiável com **AlarmManager** (`RTC_WAKEUP`) e fallback com **WorkManager**, respeitando o fuso horário local do dispositivo sem desvios.
- **Check-in Diário:** Histórico de cumprimento da suplementação diária.

### 5. Evolução, Métricas & Gráficos
- **Gráfico de Evolução de Peso Corporal:** Renderizado em tempo real com **Canvas nativo do Jetpack Compose**, exibindo histórico, variação e médias.
- **Histórico de Força:** Acompanhamento da sobrecarga progressiva em cada exercício ao longo das semanas.

### 6. Segurança, Privacidade & Isolamento de Dados
- **Isolamento Total por Usuário:** Todas as coleções do Firestore e arquivos locais são estritamente indexados pelo UID do usuário autenticado (`usuarios/{uid}/**`).
- **Regras de Produção:** O arquivo `firestore.rules` proíbe expressamente leitura ou escrita cruzada entre contas diferentes.
- **Direito ao Esquecimento (LGPD/GDPR):** O usuário pode excluir sua conta e todos os dados armazenados (biometria, histórico, fotos, treinos) a qualquer momento diretamente na aba Perfil.
- **Chaves Protegidas:** Nenhuma chave de API ou credencial sensível é exposta no código-fonte. A injeção é realizada via `BuildConfig` e Secrets Gradle Plugin.

---

## 🏗️ Arquitetura e Tecnologias

- **Linguagem:** Kotlin 2.0+
- **Interface:** Jetpack Compose (Material Design 3, tema dinâmico Claro/Escuro)
- **Padrão Arquitetural:** MVVM (Model-View-ViewModel) com Unidirectional Data Flow (StateFlow e collectAsStateWithLifecycle)
- **Persistência Local:** Room Database com Migrations estruturadas e SharedPreferences isoladas
- **Nuvem & Backend:** Firebase Authentication e Google Cloud Firestore
- **Inteligência Artificial:** Google Gemini API (gemini-2.5-flash) com fallback inteligente offline
- **Câmera & Visão Computacional:** Android CameraX e Google ML Kit Barcode Scanning
- **Agendamento em Segundo Plano:** Android WorkManager e AlarmManager

---

## 🚀 Como Compilar e Executar

### Pré-requisitos
1. Android Studio Ladybug / Koala ou superior.
2. JDK 17 ou 21 configurado.
3. Android SDK com `compileSdk = 36` e `minSdk = 24`.

### Configuração de Segurança da IA (Backend / Firebase Cloud Functions)
Para máxima segurança e conformidade de produção, as chamadas à API do Google Gemini foram completamente desacopladas do app Android e migradas para o Firebase Cloud Functions (`functions/index.js`). A chave `GEMINI_API_KEY` **NUNCA** é exposta no APK/AAB do aplicativo.

Para configurar o segredo no Firebase Secret Manager e realizar o deploy do backend:
```bash
# 1. Definir o segredo no Firebase Functions
firebase functions:secrets:set GEMINI_API_KEY

# 2. Fazer o deploy das funções de backend
firebase deploy --only functions
```

*Nota:* O aplicativo possui sistema de fallback local inteligente completo. Se o backend estiver indisponível ou em modo offline, o FitAI continua operando 100% sem interrupções.

### Comandos de Teste e Compilação
```bash
# Executar a suíte de testes unitários locais e Robolectric
gradle :app:testDebugUnitTest

# Gerar o APK instalável de Debug / Release
gradle :app:assembleDebug
gradle :app:assembleRelease
```

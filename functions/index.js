const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

// Secret gerenciado pelo Firebase Secret Manager / Google Cloud Secret Manager
const geminiApiKey = defineSecret("GEMINI_API_KEY");

// Cache em memória para rate limiting por usuário (Janela deslizante simples)
const rateLimitMap = new Map();

function checkRateLimit(uid, maxPerMinute = 15) {
  const now = Date.now();
  const windowMs = 60 * 1000;
  const userRate = rateLimitMap.get(uid) || { count: 0, resetTime: now + windowMs };

  if (now > userRate.resetTime) {
    userRate.count = 0;
    userRate.resetTime = now + windowMs;
  }

  userRate.count++;
  rateLimitMap.set(uid, userRate);

  return userRate.count <= maxPerMinute;
}

/**
 * Função Callable segura: FitAI Android -> Firebase Callable -> Gemini
 * Autenticação do Firebase Auth é validada automaticamente.
 * A chave de API do Gemini NUNCA trafega para o dispositivo cliente.
 */
exports.fitAiGenerateContent = onCall(
  {
    secrets: [geminiApiKey],
    cors: true,
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (request) => {
    // 1. Verificação estrita de autenticação
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError(
        "unauthenticated",
        "Acesso negado: Usuário deve estar autenticado no FitAI para utilizar recursos de Inteligência Artificial."
      );
    }

    const uid = request.auth.uid;

    // 2. Proteção contra abuso e rate limit
    if (!checkRateLimit(uid, 15)) {
      throw new HttpsError(
        "resource-exhausted",
        "Limite de requisições excedido. Por favor, aguarde 1 minuto antes de tentar novamente."
      );
    }

    // 3. Validação do payload
    const data = request.data || {};
    const contents = data.contents;
    if (!contents || !Array.isArray(contents) || contents.length === 0) {
      throw new HttpsError("invalid-argument", "Payload inválido: 'contents' é obrigatório.");
    }

    // Limite de tamanho de requisição para evitar ataques DoS com buffers gigantes (7MB max para fotos)
    const payloadStr = JSON.stringify(data);
    if (payloadStr.length > 7 * 1024 * 1024) {
      throw new HttpsError("invalid-argument", "Payload muito grande. Reduza a resolução da foto ou o tamanho do texto.");
    }

    // 4. Obtenção segura da chave do Gemini a partir do ambiente servidor
    const apiKey = geminiApiKey.value() || process.env.GEMINI_API_KEY;
    if (!apiKey) {
      console.error("ERRO CRÍTICO: GEMINI_API_KEY não configurada no servidor.");
      throw new HttpsError("internal", "Configuração de IA indisponível no servidor.");
    }

    // 5. Chamada segura para a API do Gemini a partir do backend
    const model = data.model || "gemini-2.5-flash";
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

    const geminiPayload = {
      contents: data.contents,
    };
    if (data.systemInstruction) {
      geminiPayload.systemInstruction = data.systemInstruction;
    }
    if (data.generationConfig) {
      geminiPayload.generationConfig = data.generationConfig;
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(geminiPayload),
      });

      if (!response.ok) {
        const errText = await response.text();
        console.error(`Gemini API Error (${response.status}):`, errText);
        throw new HttpsError("internal", `Erro retornado pelo motor de IA: ${response.status}`);
      }

      const result = await response.json();
      return result;
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      console.error("Erro na comunicação com a API do Gemini:", err.message);
      throw new HttpsError("internal", "Falha de comunicação com o serviço de IA.");
    }
  }
);

/**
 * Endpoint HTTP REST alternativo seguro com validação de Firebase ID Token no header Authorization
 */
exports.fitAiGenerateContentHttp = onRequest(
  {
    secrets: [geminiApiKey],
    cors: true,
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Método não permitido. Use POST." });
    }

    const authHeader = req.headers.authorization || "";
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Acesso negado: Bearer token ausente." });
    }

    const idToken = authHeader.split("Bearer ")[1].trim();
    let decodedToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(idToken);
    } catch (e) {
      return res.status(401).json({ error: "Token de autenticação inválido ou expirado." });
    }

    const uid = decodedToken.uid;
    if (!checkRateLimit(uid, 15)) {
      return res.status(429).json({ error: "Limite de requisições excedido. Aguarde 1 minuto." });
    }

    const data = req.body || {};
    const contents = data.contents;
    if (!contents || !Array.isArray(contents) || contents.length === 0) {
      return res.status(400).json({ error: "Payload inválido: 'contents' é obrigatório." });
    }

    const apiKey = geminiApiKey.value() || process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return res.status(500).json({ error: "Configuração de IA indisponível no servidor." });
    }

    const model = data.model || "gemini-2.5-flash";
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

    const geminiPayload = {
      contents: data.contents,
    };
    if (data.systemInstruction) geminiPayload.systemInstruction = data.systemInstruction;
    if (data.generationConfig) geminiPayload.generationConfig = data.generationConfig;

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(geminiPayload),
      });

      if (!response.ok) {
        const errText = await response.text();
        return res.status(response.status).json({ error: errText });
      }

      const result = await response.json();
      return res.json(result);
    } catch (err) {
      return res.status(500).json({ error: "Falha de comunicação com o serviço de IA: " + err.message });
    }
  }
);

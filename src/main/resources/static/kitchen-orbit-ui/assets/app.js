(function () {
  function parseBoolean(value) {
    if (typeof value !== "string") {
      return null;
    }
    const normalized = value.trim().toLowerCase();
    if (normalized === "1" || normalized === "true" || normalized === "yes") {
      return true;
    }
    if (normalized === "0" || normalized === "false" || normalized === "no") {
      return false;
    }
    return null;
  }

  const urlParams = new URLSearchParams(window.location.search || "");
  const fallbackOverride = parseBoolean(urlParams.get("fallbackToMock"));
  const autoConnectApiOverride = parseBoolean(urlParams.get("autoConnectApi"));

  const CONFIG = Object.assign({}, window.KITCHEN_ORBIT_CONFIG || {}, {
    apiBaseUrl: urlParams.get("apiBaseUrl") || (window.KITCHEN_ORBIT_CONFIG && window.KITCHEN_ORBIT_CONFIG.apiBaseUrl) || "",
    fallbackToMock: fallbackOverride === null
      ? (window.KITCHEN_ORBIT_CONFIG && window.KITCHEN_ORBIT_CONFIG.fallbackToMock)
      : fallbackOverride,
    autoConnectApi: autoConnectApiOverride === null
      ? (window.KITCHEN_ORBIT_CONFIG && window.KITCHEN_ORBIT_CONFIG.autoConnectApi)
      : autoConnectApiOverride,
  });

  const suggestedQuestions = [
    "今天吃什么？最好 30 分钟内做完。",
    "有什么适合晚餐的清淡菜？",
    "鸡胸肉能做哪些不柴的菜？",
    "适合招待朋友的硬菜推荐一下。",
  ];

  const mockRecipes = [
    {
      title: "宫保鸡丁",
      category: "meat_dish",
      difficulty: "进阶",
      time: 30,
      flavor: "酸甜微辣",
      ingredients: ["鸡腿肉", "花生", "干辣椒"],
      summary: "适合下饭的经典快炒，兼顾香、辣、甜、酸四个方向。",
    },
    {
      title: "清蒸鲈鱼",
      category: "aquatic",
      difficulty: "进阶",
      time: 25,
      flavor: "鲜香清淡",
      ingredients: ["鲈鱼", "姜", "葱"],
      summary: "适合晚餐和宴客的稳妥菜，突出鱼肉本身鲜味。",
    },
    {
      title: "鸡蛋三明治",
      category: "breakfast",
      difficulty: "新手友好",
      time: 10,
      flavor: "柔和咸香",
      ingredients: ["吐司", "鸡蛋", "蛋黄酱"],
      summary: "出餐快，适合早餐与加班日轻食。",
    },
    {
      title: "杨枝甘露",
      category: "drink",
      difficulty: "新手友好",
      time: 15,
      flavor: "清爽果香",
      ingredients: ["芒果", "西米", "椰浆"],
      summary: "夏天友好的甜品型饮品，适合聚会或饭后。",
    },
    {
      title: "羊肉汤",
      category: "soup",
      difficulty: "宴客",
      time: 90,
      flavor: "浓郁醇厚",
      ingredients: ["羊肉", "白萝卜", "姜"],
      summary: "适合秋冬场景，强调暖胃和饱腹感。",
    },
    {
      title: "金汤力",
      category: "drink",
      difficulty: "新手友好",
      time: 5,
      flavor: "清爽微苦",
      ingredients: ["金酒", "汤力水", "青柠"],
      summary: "标准鸡尾酒入门款，适合夜间轻社交。",
    },
    {
      title: "番茄炒蛋",
      category: "meat_dish",
      difficulty: "新手友好",
      time: 12,
      flavor: "酸甜家常",
      ingredients: ["番茄", "鸡蛋", "葱"],
      summary: "高频家常菜，适合清淡快手晚餐。",
    },
    {
      title: "陈皮排骨汤",
      category: "soup",
      difficulty: "进阶",
      time: 60,
      flavor: "鲜甜回甘",
      ingredients: ["排骨", "陈皮", "玉米"],
      summary: "汤感温和，适合搭配主食形成完整晚餐。",
    },
  ];

  const categoryLabels = {
    breakfast: "早餐",
    drink: "饮品",
    aquatic: "水产",
    soup: "汤羹",
    meat_dish: "荤菜",
  };

  const CITATION_DETAIL_KEY = "kitchen_orbit_citation_detail";

  function createEl(tag, className, text) {
    const el = document.createElement(tag);
    if (className) {
      el.className = className;
    }
    if (text !== undefined) {
      el.textContent = text;
    }
    return el;
  }

  function escapeHtml(text) {
    return (text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function renderMarkdownToHtml(markdown) {
    const escaped = escapeHtml(markdown || "");
    const lines = escaped.split(/\r?\n/);
    let html = "";
    let inList = false;

    lines.forEach((line) => {
      const text = line.trim();
      if (!text) {
        if (inList) {
          html += "</ul>";
          inList = false;
        }
        html += "<p class=\"h-3\"></p>";
        return;
      }

      const headingMatch = text.match(/^(#{1,3})\s+(.*)$/);
      if (headingMatch) {
        if (inList) {
          html += "</ul>";
          inList = false;
        }
        const level = headingMatch[1].length;
        const headingClass = level === 1
          ? "text-lg font-semibold text-stone-100"
          : "text-base font-semibold text-stone-100";
        html += "<h" + level + " class=\"" + headingClass + "\">" + headingMatch[2] + "</h" + level + ">";
        return;
      }

      const listMatch = text.match(/^[-*]\s+(.*)$/);
      if (listMatch) {
        if (!inList) {
          html += "<ul class=\"list-disc space-y-1 pl-5\">";
          inList = true;
        }
        html += "<li>" + listMatch[1] + "</li>";
        return;
      }

      if (inList) {
        html += "</ul>";
        inList = false;
      }
      const numbered = text.replace(/^(\d+)\.\s+/g, "<span class=\"font-semibold text-stone-200\">$1.</span> ");
      html += "<p class=\"leading-7\">" + numbered + "</p>";
    });

    if (inList) {
      html += "</ul>";
    }

    return html;
  }

  function getMockAnswer(question) {
    if (question.includes("今天吃什么")) {
      return {
        answer:
          "如果你现在没有明确偏好，建议从“快手主菜 + 一道汤 + 一个轻饮”去配。今天可以先做番茄炒蛋或宫保鸡丁，再配一份陈皮排骨汤；如果只想做一锅完成度高的晚餐，清蒸鲈鱼会更稳，口味清爽、出错率也低。",
        followups: ["想吃清淡一点的版本", "30 分钟内能完成哪些菜", "如果只有鸡蛋和番茄怎么办"],
        citations: [
          {
            recipeId: 201,
            recipeName: "番茄炒蛋",
            chunkId: "recipe_201_summary_1",
            sectionType: "summary",
            score: 0.91,
            hitSource: "chunk",
          },
          {
            recipeId: 101,
            recipeName: "宫保鸡丁",
            chunkId: "recipe_101_steps_2",
            sectionType: "steps",
            score: 0.86,
            hitSource: "metadata",
          },
        ],
        sources: ["番茄炒蛋", "宫保鸡丁", "清蒸鲈鱼", "陈皮排骨汤"],
      };
    }

    if (question.includes("鸡") || question.includes("鸡胸") || question.includes("鸡肉")) {
      return {
        answer:
          "鸡肉相关菜建议优先选受热更稳定、容错更高的做法。想避免鸡胸肉发柴，可以走快炒、滑炒或带一点酱汁的路线；如果你接受更重口的方案，宫保鸡丁是更容易做出层次感的一道。",
        followups: ["鸡胸肉怎么腌更嫩", "鸡肉适合搭配哪些蔬菜", "推荐一份低脂鸡肉菜谱"],
        sources: ["宫保鸡丁", "鸡蛋三明治"],
      };
    }

    if (question.includes("清淡") || question.includes("晚餐")) {
      return {
        answer:
          "清淡晚餐可以优先考虑清蒸鲈鱼、番茄炒蛋和陈皮排骨汤这类低负担组合。结构上建议一荤一素一汤，如果你只想做一道主菜，清蒸鲈鱼最合适，成菜稳定、调味克制，也适合继续扩展成营养分析场景。",
        followups: ["晚餐热量低一点的组合", "有没有适合减脂的晚餐", "如果没有鱼可以换什么"],
        sources: ["清蒸鲈鱼", "番茄炒蛋", "陈皮排骨汤"],
      };
    }

    return {
      answer:
        "这个前端演示页当前会先用本地 mock 数据生成回答，重点是确认交互结构和信息排布。后续接入真实 RAG 后，这里可以返回检索摘要、食材要点、分步做法以及来源文档。",
      followups: ["展示一下检索来源结构", "后续怎么接 FastAPI", "这个页面还能加哪些能力"],
      sources: ["Mock Response", "Cook RAG"],
    };
  }

  function getOrCreateUserId() {
    const key = "kitchen_orbit_user_id";
    const existing = window.localStorage.getItem(key);
    if (existing) {
      return existing;
    }
    const uid = "u_web_" + Math.random().toString(36).slice(2, 10);
    window.localStorage.setItem(key, uid);
    return uid;
  }

  function getCitationDetailPreference() {
    return window.localStorage.getItem(CITATION_DETAIL_KEY) !== "0";
  }

  function saveCitationDetailPreference(enabled) {
    window.localStorage.setItem(CITATION_DETAIL_KEY, enabled ? "1" : "0");
  }

  function unescapeJsonStringFragment(text) {
    if (!text) {
      return "";
    }
    return text
      .replace(/\\n/g, "\n")
      .replace(/\\r/g, "\r")
      .replace(/\\t/g, "\t")
      .replace(/\\\"/g, '"')
      .replace(/\\\\/g, "\\")
      .replace(/\\u([0-9a-fA-F]{4})/g, function (_, hex) {
        return String.fromCharCode(parseInt(hex, 16));
      });
  }

  function extractAnswerFromEnvelope(rawText) {
    const source = typeof rawText === "string" ? rawText : "";
    const trimmed = source.trim();
    if (!trimmed) {
      return "";
    }

    if (!trimmed.startsWith("{") || trimmed.indexOf('"answer"') === -1) {
      return source;
    }

    try {
      const parsed = JSON.parse(trimmed);
      if (parsed && typeof parsed.answer === "string") {
        return parsed.answer;
      }
    } catch (error) {
      // Stream delta may be incomplete JSON; fallback to partial extraction.
    }

    const answerKeyIndex = source.indexOf('"answer"');
    if (answerKeyIndex < 0) {
      return source;
    }
    const colonIndex = source.indexOf(":", answerKeyIndex);
    if (colonIndex < 0) {
      return "";
    }
    const quoteStart = source.indexOf('"', colonIndex + 1);
    if (quoteStart < 0) {
      return "";
    }

    let escaped = false;
    let i = quoteStart + 1;
    for (; i < source.length; i += 1) {
      const ch = source[i];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (ch === "\\") {
        escaped = true;
        continue;
      }
      if (ch === '"') {
        break;
      }
    }
    const fragment = source.slice(quoteStart + 1, i);
    return unescapeJsonStringFragment(fragment);
  }

  function normalizeApiChatResult(result) {
    const data = (result && result.data) || {};
    const citations = Array.isArray(data.citations) ? data.citations : [];
    const sources = citations.map((citation) => citation.recipeName || "未知来源");
    const answer = extractAnswerFromEnvelope(data.answer || "");

    return {
      answer: answer || "未返回回答内容",
      followups: Array.isArray(data.followups) ? data.followups : [],
      citations,
      sources,
      debug: data.debug || null,
      intent: data.intent || "",
      sessionId: data.sessionId || "",
    };
  }

  function shouldUseApi() {
    return Boolean(CONFIG.chatEndpoint) || CONFIG.autoConnectApi === true;
  }

  function resolveApiEndpoint(endpointPath, fallbackPath) {
    const raw = endpointPath || fallbackPath;
    if (/^https?:\/\//i.test(raw)) {
      return raw;
    }
    const base = (CONFIG.apiBaseUrl || "").trim();
    if (!base) {
      return raw;
    }
    const cleanBase = base.endsWith("/") ? base.slice(0, -1) : base;
    const cleanPath = raw.startsWith("/") ? raw : ("/" + raw);
    return cleanBase + cleanPath;
  }

  async function createSession(userId) {
    const endpoint = resolveApiEndpoint(CONFIG.sessionEndpoint, "/api/session");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId }),
    });
    if (!response.ok) {
      throw new Error("Session API request failed");
    }
    const body = await response.json();
    const sessionId = body && body.data && body.data.sessionId;
    if (!sessionId) {
      throw new Error("Session API response missing sessionId");
    }
    return sessionId;
  }

  async function askAssistantViaApi(question, qaState) {
    if (!qaState.sessionId) {
      qaState.sessionId = await createSession(qaState.userId);
    }

    const endpoint = resolveApiEndpoint(CONFIG.chatEndpoint, "/api/chat");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: qaState.sessionId,
        userId: qaState.userId,
        message: question,
        stream: false,
        context: {
          page: "qa",
          clientTime: new Date().toISOString(),
        },
      }),
    });
    if (!response.ok) {
      throw new Error("Chat API request failed");
    }

    const body = await response.json();
    if (!body || body.code !== "0") {
      throw new Error("Chat API business error");
    }
    return normalizeApiChatResult(body);
  }

  function parseSseFrame(frameText) {
    const lines = frameText.split("\n");
    let eventName = "message";
    const dataParts = [];
    lines.forEach((line) => {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim() || "message";
        return;
      }
      if (line.startsWith("data:")) {
        dataParts.push(line.slice(5).trim());
      }
    });
    const rawData = dataParts.join("\n");
    let payload = rawData;
    if (rawData) {
      try {
        payload = JSON.parse(rawData);
      } catch (error) {
        payload = rawData;
      }
    }
    return { eventName, payload };
  }

  async function askAssistantViaApiStream(question, qaState, handlers) {
    if (!qaState.sessionId) {
      qaState.sessionId = await createSession(qaState.userId);
    }

    const endpoint = resolveApiEndpoint(CONFIG.chatStreamEndpoint, "/api/chat/stream");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: qaState.sessionId,
        userId: qaState.userId,
        message: question,
        stream: true,
        context: {
          page: "qa",
          clientTime: new Date().toISOString(),
        },
      }),
    });

    if (!response.ok || !response.body) {
      throw new Error("Chat stream API request failed");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    let donePayload = null;

    while (true) {
      const readResult = await reader.read();
      if (readResult.done) {
        break;
      }
      buffer += decoder.decode(readResult.value, { stream: true });
      const frames = buffer.split("\n\n");
      buffer = frames.pop() || "";

      frames.forEach((frame) => {
        if (!frame.trim()) {
          return;
        }
        const parsed = parseSseFrame(frame);
        if (parsed.eventName === "delta") {
          const deltaText = parsed.payload && parsed.payload.delta ? parsed.payload.delta : "";
          if (deltaText && handlers && handlers.onDelta) {
            handlers.onDelta(deltaText);
          }
          return;
        }
        if (parsed.eventName === "status") {
          if (handlers && handlers.onStatus) {
            handlers.onStatus(parsed.payload);
          }
          return;
        }
        if (parsed.eventName === "error") {
          const msg = parsed.payload && parsed.payload.message ? parsed.payload.message : "stream error";
          throw new Error(msg);
        }
        if (parsed.eventName === "done") {
          donePayload = parsed.payload;
        }
      });
    }

    if (!donePayload || donePayload.code !== "0") {
      throw new Error((donePayload && donePayload.message) || "Chat stream API business error");
    }
    return normalizeApiChatResult(donePayload);
  }

  async function askAssistant(question, qaState) {
    if (shouldUseApi()) {
      try {
        return await askAssistantViaApi(question, qaState);
      } catch (error) {
        if (CONFIG.fallbackToMock === false) {
          throw error;
        }
      }
    }
    return getMockAnswer(question);
  }

  function renderFollowups(wrapper, extra) {
    if (!extra || !extra.followups || !extra.followups.length) {
      return;
    }
    const followupWrap = createEl("div", "flex flex-wrap gap-2");
    extra.followups.forEach((item) => {
      const button = createEl("button", "orbit-chip border-0", item);
      button.type = "button";
      button.addEventListener("click", function () {
        const input = document.getElementById("chat-input");
        if (input) {
          input.value = item;
          input.focus();
        }
      });
      followupWrap.appendChild(button);
    });
    wrapper.appendChild(followupWrap);
  }

  function renderDebugInfo(wrapper, extra) {
    if (!extra || !extra.debug) {
      return;
    }
    const detail = [];
    if (extra.intent) {
      detail.push("intent=" + extra.intent);
    }
    if (extra.debug.route) {
      detail.push("route=" + extra.debug.route);
    }
    if (typeof extra.debug.retrievalCount === "number") {
      detail.push("retrievalCount=" + extra.debug.retrievalCount);
    }
    if (extra.sessionId) {
      detail.push("sessionId=" + extra.sessionId);
    }
    if (!detail.length) {
      return;
    }

    const debugEl = createEl("p", "text-[11px] leading-6 text-stone-500", "debug: " + detail.join(" | "));
    wrapper.appendChild(debugEl);
  }

  function renderSources(wrapper, extra) {
    if (!extra) {
      return;
    }

    const detailToggle = document.getElementById("citation-detail-toggle");
    const showCitationDetail = !detailToggle || detailToggle.checked;

    if (extra.citations && extra.citations.length) {
      const citationsWrap = createEl("div", "space-y-3");
      const groupedBySource = extra.citations.reduce((acc, citation) => {
        const sourceKey = citation.hitSource || "unknown";
        if (!acc[sourceKey]) {
          acc[sourceKey] = [];
        }
        acc[sourceKey].push(citation);
        return acc;
      }, {});

      Object.entries(groupedBySource).forEach(([sourceType, citationList]) => {
        const groupWrap = createEl("div", "space-y-2");
        const groupTitle = createEl(
          "p",
          "text-[11px] uppercase tracking-[0.22em] text-stone-500",
          "source=" + sourceType + "（" + citationList.length + "）"
        );
        groupWrap.appendChild(groupTitle);

        const groupGrid = createEl("div", "grid gap-2 md:grid-cols-2");
        citationList.forEach((citation) => {
          const sourceEl = createEl("div", "source-card");
          const title = createEl(
            "p",
            "mb-1 text-sm font-semibold text-stone-100",
            (citation.recipeName || "未知菜谱") + " #" + (citation.recipeId || "-")
          );
          const meta = createEl(
            "p",
            "text-xs leading-6 text-stone-300",
            showCitationDetail
              ? [
                  "section=" + (citation.sectionType || "unknown"),
                  "source=" + (citation.hitSource || "unknown"),
                  "score=" + (typeof citation.score === "number" ? citation.score.toFixed(2) : "-")
                ].join(" | ")
              : "source=" + (citation.hitSource || "unknown")
          );
          sourceEl.appendChild(title);
          sourceEl.appendChild(meta);

          if (showCitationDetail) {
            const footer = createEl("div", "mt-1 flex items-center justify-between gap-2");
            const chunk = createEl(
              "p",
              "text-[11px] leading-5 text-stone-400",
              "chunkId=" + (citation.chunkId || "-")
            );
            footer.appendChild(chunk);

            if (citation.chunkId) {
              const copyButton = createEl("button", "orbit-chip border-0 text-[10px]", "复制 chunkId");
              copyButton.type = "button";
              copyButton.addEventListener("click", async function () {
                try {
                  await navigator.clipboard.writeText(citation.chunkId);
                  copyButton.textContent = "已复制";
                  window.setTimeout(function () {
                    copyButton.textContent = "复制 chunkId";
                  }, 1200);
                } catch (error) {
                  copyButton.textContent = "复制失败";
                }
              });
              footer.appendChild(copyButton);
            }
            sourceEl.appendChild(footer);
          }
          groupGrid.appendChild(sourceEl);
        });

        groupWrap.appendChild(groupGrid);
        citationsWrap.appendChild(groupWrap);
      });
      wrapper.appendChild(citationsWrap);
      return;
    }

    if (extra.sources && extra.sources.length) {
      const sourcesWrap = createEl("div", "grid gap-2 md:grid-cols-2");
      extra.sources.forEach((source) => {
        const sourceEl = createEl("div", "source-card");
        const label = createEl("p", "mb-1 text-xs uppercase tracking-[0.24em] text-stone-500", "Source");
        const value = createEl("p", "text-sm font-medium text-stone-200", source);
        sourceEl.appendChild(label);
        sourceEl.appendChild(value);
        sourcesWrap.appendChild(sourceEl);
      });
      wrapper.appendChild(sourcesWrap);
    }
  }

  function appendMessage(container, role, content, extra) {
    const wrapper = createEl("article", "space-y-3");
    const title = createEl(
      "div",
      "flex items-center gap-3 text-xs uppercase tracking-[0.25em] text-stone-400",
      role === "user" ? "User Prompt" : "Assistant Reply"
    );
    wrapper.appendChild(title);

    const bubble = createEl(
      "div",
      "chat-bubble " + (role === "user" ? "chat-bubble--user" : "chat-bubble--assistant")
    );
    if (role === "assistant") {
      bubble.innerHTML = renderMarkdownToHtml(content);
    } else {
      bubble.textContent = content;
    }
    wrapper.appendChild(bubble);

    renderSources(wrapper, extra);
    renderDebugInfo(wrapper, extra);
    renderFollowups(wrapper, extra);

    container.appendChild(wrapper);
    container.scrollTop = container.scrollHeight;
  }

  function appendTyping(container) {
    const typing = createEl("div", "chat-bubble chat-bubble--assistant text-stone-400");
    typing.id = "typing-indicator";
    typing.textContent = "正在整理回答...";
    container.appendChild(typing);
    container.scrollTop = container.scrollHeight;
  }

  function removeTyping() {
    const typing = document.getElementById("typing-indicator");
    if (typing) {
      typing.remove();
    }
  }

  function createStreamingAssistantMessage(container) {
    const wrapper = createEl("article", "space-y-3");
    const title = createEl(
      "div",
      "flex items-center gap-3 text-xs uppercase tracking-[0.25em] text-stone-400",
      "Assistant Reply"
    );
    const bubble = createEl("div", "chat-bubble chat-bubble--assistant");
    bubble.innerHTML = "<p class=\"text-stone-400\">正在生成中...</p>";
    wrapper.appendChild(title);
    wrapper.appendChild(bubble);
    container.appendChild(wrapper);

    return {
      appendDelta: function (deltaText) {
        const current = bubble.getAttribute("data-raw") || "";
        const next = current + deltaText;
        bubble.setAttribute("data-raw", next);
        const display = extractAnswerFromEnvelope(next);
        bubble.innerHTML = renderMarkdownToHtml(display || "正在生成中...");
        container.scrollTop = container.scrollHeight;
      },
      finish: function (extra) {
        const current = bubble.getAttribute("data-raw") || "";
        bubble.innerHTML = renderMarkdownToHtml((extra && extra.answer) || current);
        renderSources(wrapper, extra);
        renderDebugInfo(wrapper, extra);
        renderFollowups(wrapper, extra);
        container.scrollTop = container.scrollHeight;
      },
    };
  }

  function initQA() {
    const messages = document.getElementById("chat-messages");
    const input = document.getElementById("chat-input");
    const sendButton = document.getElementById("send-button");
    const suggestionList = document.getElementById("suggestion-list");
    const streamToggle = document.getElementById("stream-toggle");
    const detailToggle = document.getElementById("citation-detail-toggle");

    if (!messages || !input || !sendButton || !suggestionList || !streamToggle) {
      return;
    }

    const qaState = {
      userId: getOrCreateUserId(),
      sessionId: null,
    };

    if (detailToggle) {
      detailToggle.checked = getCitationDetailPreference();
      detailToggle.addEventListener("change", function () {
        saveCitationDetailPreference(detailToggle.checked);
      });
    }

    appendMessage(
      messages,
      "assistant",
      "欢迎进入 Kitchen Orbit。这个页面先验证问答交互结构，后续接上真实 RAG 后，这里会承接检索结果、分步回答和来源引用。",
      {
        followups: ["今天吃什么？", "有什么适合晚餐的清淡菜？", "鸡胸肉怎么做更嫩？"],
      }
    );

    suggestedQuestions.forEach((question) => {
      const button = createEl("button", "orbit-chip border-0", question);
      button.type = "button";
      button.addEventListener("click", function () {
        input.value = question;
        input.focus();
      });
      suggestionList.appendChild(button);
    });

    async function handleSubmit() {
      const question = input.value.trim();
      if (!question) {
        return;
      }

      appendMessage(messages, "user", question);
      input.value = "";
      appendTyping(messages);
      sendButton.disabled = true;
      sendButton.style.opacity = "0.7";

      try {
        if (streamToggle.checked && shouldUseApi()) {
          const streamView = createStreamingAssistantMessage(messages);
          removeTyping();
          const streamResult = await askAssistantViaApiStream(question, qaState, {
            onDelta: function (delta) {
              streamView.appendDelta(delta);
            },
          });
          streamView.finish(streamResult);
        } else {
          const result = await askAssistant(question, qaState);
          removeTyping();
          appendMessage(messages, "assistant", result.answer, result);
        }
      } catch (error) {
        removeTyping();
        const endpoint = resolveApiEndpoint(CONFIG.chatEndpoint, "/api/chat");
        appendMessage(
          messages,
          "assistant",
          "当前未能拿到回答，请检查后端服务和接口配置。",
          { sources: ["endpoint=" + endpoint, "error=" + (error && error.message ? error.message : "unknown")] }
        );
      } finally {
        sendButton.disabled = false;
        sendButton.style.opacity = "1";
      }
    }

    sendButton.addEventListener("click", handleSubmit);
    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        handleSubmit();
      }
    });
  }

  function renderRecipes(items, summaryText) {
    const grid = document.getElementById("recipe-grid");
    const summary = document.getElementById("result-summary");
    if (!grid || !summary) {
      return;
    }

    grid.innerHTML = "";
    summary.textContent = summaryText || `共 ${items.length} 道结果`;

    if (!items.length) {
      const empty = createEl("div", "recipe-card md:col-span-2 xl:col-span-3");
      empty.innerHTML =
        '<p class="mb-2 text-lg font-semibold text-stone-100">没有匹配结果</p><p class="text-sm leading-7 text-stone-300">可以换一个关键词，或放宽分类、难度和时长条件。</p>';
      grid.appendChild(empty);
      return;
    }

    items.forEach((recipe) => {
      const card = createEl("article", "recipe-card");
      const head = createEl("div", "mb-4 flex items-start justify-between gap-4");
      const titleWrap = createEl("div");
      const title = createEl("h3", "text-xl font-semibold text-stone-100", recipe.title || recipe.name || "未命名菜谱");
      const cookTimeMinutes = recipe.time !== undefined ? recipe.time : recipe.cookTimeMinutes;
      const meta = createEl(
        "p",
        "mt-2 text-sm text-stone-400",
        `${categoryLabels[recipe.category] || recipe.category || "未知分类"} / ${recipe.difficulty || "未知难度"} / ${cookTimeMinutes || "-"} 分钟`
      );
      titleWrap.appendChild(title);
      titleWrap.appendChild(meta);
      head.appendChild(titleWrap);
      const badge = createEl(
        "span",
        "status-pill",
        recipe.flavor || (typeof recipe.score === "number" ? `score ${recipe.score.toFixed(2)}` : "检索结果")
      );
      head.appendChild(badge);
      card.appendChild(head);

      const summaryText = createEl("p", "mb-4 text-sm leading-7 text-stone-300", recipe.summary || "暂无摘要");
      card.appendChild(summaryText);

      const tags = createEl("div", "mb-4 flex flex-wrap gap-2");
      const tagList = Array.isArray(recipe.ingredients)
        ? recipe.ingredients
        : (Array.isArray(recipe.tags) ? recipe.tags : []);
      tagList.forEach((ingredient) => {
        tags.appendChild(createEl("span", "recipe-tag", ingredient));
      });
      if (!tagList.length) {
        tags.appendChild(createEl("span", "recipe-tag", "暂无标签"));
      }
      card.appendChild(tags);

      const footer = createEl("div", "flex items-center justify-between");
      footer.appendChild(createEl("span", "text-xs uppercase tracking-[0.24em] text-stone-500", "Recipe Card"));
      footer.appendChild(createEl("button", "orbit-chip border-0", "查看详情"));
      card.appendChild(footer);

      grid.appendChild(card);
    });
  }

  function initSearch() {
    const searchInput = document.getElementById("search-input");
    const categoryFilter = document.getElementById("category-filter");
    const difficultyFilter = document.getElementById("difficulty-filter");
    const timeFilter = document.getElementById("time-filter");
    const resetFilters = document.getElementById("reset-filters");
    const presetButtons = document.querySelectorAll(".recipe-preset");
    const sortFilter = document.getElementById("sort-filter");
    const pageSizeFilter = document.getElementById("page-size-filter");
    const prevPageButton = document.getElementById("prev-page");
    const nextPageButton = document.getElementById("next-page");
    const pageIndicator = document.getElementById("page-indicator");

    if (!searchInput || !categoryFilter || !difficultyFilter || !timeFilter || !resetFilters) {
      return;
    }

    const defaultSortBy = CONFIG.searchDefaultSortBy || "relevance";
    const defaultPageSize = Math.max(
      1,
      Number(CONFIG.searchDefaultPageSize || (pageSizeFilter ? pageSizeFilter.value : 12) || 12)
    );

    if (sortFilter) {
      sortFilter.value = defaultSortBy;
    }
    if (pageSizeFilter) {
      pageSizeFilter.value = String(defaultPageSize);
    }

    const searchState = {
      pageNo: 1,
      pageSize: defaultPageSize,
      total: 0,
    };

    function shouldUseSearchApi() {
      return Boolean(CONFIG.searchEndpoint) || CONFIG.autoConnectApi === true;
    }

    function buildSearchPayload() {
      const query = searchInput.value.trim();
      const category = categoryFilter.value;
      const difficulty = difficultyFilter.value;
      const timeValue = timeFilter.value;
      const filters = {};

      if (category) {
        filters.category = category;
      }
      if (difficulty) {
        filters.difficulty = difficulty;
      }
      if (timeValue && timeValue !== "999") {
        filters.maxCookTimeMinutes = Number(timeValue);
      }

      return {
        query,
        filters,
        pageNo: searchState.pageNo,
        pageSize: searchState.pageSize,
          sortBy: (sortFilter && sortFilter.value) || defaultSortBy,
      };
    }

    async function searchViaApi() {
      const endpoint = resolveApiEndpoint(CONFIG.searchEndpoint, "/api/search");
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildSearchPayload()),
      });
      if (!response.ok) {
        throw new Error("Search API request failed");
      }

      const body = await response.json();
      if (!body || body.code !== "0") {
        throw new Error("Search API business error");
      }

      const items = body.data && Array.isArray(body.data.items) ? body.data.items : [];
      const total = body.data && typeof body.data.total === "number" ? body.data.total : items.length;
      const pageNo = body.data && typeof body.data.pageNo === "number" ? body.data.pageNo : searchState.pageNo;
      const pageSize = body.data && typeof body.data.pageSize === "number" ? body.data.pageSize : searchState.pageSize;
      if (timeFilter.value === "999") {
        return {
          items: items.filter((item) => (item.cookTimeMinutes || 0) > 45),
          total,
          pageNo,
          pageSize,
        };
      }
      return { items, total, pageNo, pageSize };
    }

    function compareRecipes(a, b, sortBy) {
      if (sortBy === "time_asc") {
        return (a.time || a.cookTimeMinutes || 0) - (b.time || b.cookTimeMinutes || 0);
      }
      if (sortBy === "time_desc") {
        return (b.time || b.cookTimeMinutes || 0) - (a.time || a.cookTimeMinutes || 0);
      }
      if (sortBy === "score_desc") {
        return (b.score || 0) - (a.score || 0);
      }
      return 0;
    }

    function buildSummaryText(count, total, pageNo, pageSize) {
      const totalPages = Math.max(1, Math.ceil((total || 0) / Math.max(1, pageSize || 1)));
      return `共 ${total} 道结果 · 第 ${pageNo}/${totalPages} 页 · 本页 ${count} 道`;
    }

    function updatePager() {
      const totalPages = Math.max(1, Math.ceil((searchState.total || 0) / Math.max(1, searchState.pageSize || 1)));
      if (pageIndicator) {
        pageIndicator.textContent = `第 ${searchState.pageNo}/${totalPages} 页`;
      }
      if (prevPageButton) {
        prevPageButton.disabled = searchState.pageNo <= 1;
        prevPageButton.style.opacity = prevPageButton.disabled ? "0.45" : "1";
      }
      if (nextPageButton) {
        nextPageButton.disabled = searchState.pageNo >= totalPages;
        nextPageButton.style.opacity = nextPageButton.disabled ? "0.45" : "1";
      }
    }

    Object.entries(categoryLabels).forEach(([value, label]) => {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = label;
      categoryFilter.appendChild(option);
    });

    async function applyFilters(resetPageNo) {
      if (resetPageNo) {
        searchState.pageNo = 1;
      }
      const query = searchInput.value.trim().toLowerCase();
      const category = categoryFilter.value;
      const difficulty = difficultyFilter.value;
      const timeValue = timeFilter.value;
      const sortBy = (sortFilter && sortFilter.value) || defaultSortBy;

      if (shouldUseSearchApi()) {
        try {
          const apiResult = await searchViaApi();
          searchState.total = Math.max(0, apiResult.total || 0);
          searchState.pageNo = Math.max(1, apiResult.pageNo || searchState.pageNo);
          searchState.pageSize = Math.max(1, apiResult.pageSize || searchState.pageSize);
          renderRecipes(
            apiResult.items,
            buildSummaryText(apiResult.items.length, searchState.total, searchState.pageNo, searchState.pageSize)
          );
          updatePager();
          return;
        } catch (error) {
          if (CONFIG.fallbackToMock === false) {
            searchState.total = 0;
            renderRecipes([], buildSummaryText(0, 0, 1, searchState.pageSize));
            updatePager();
            return;
          }
        }
      }

      const filtered = mockRecipes.filter((recipe) => {
        const matchesQuery =
          !query ||
          recipe.title.toLowerCase().includes(query) ||
          recipe.summary.toLowerCase().includes(query) ||
          recipe.flavor.toLowerCase().includes(query) ||
          recipe.ingredients.some((ingredient) => ingredient.toLowerCase().includes(query));

        const matchesCategory = !category || recipe.category === category;
        const matchesDifficulty = !difficulty || recipe.difficulty === difficulty;

        let matchesTime = true;
        if (timeValue === "15" || timeValue === "30" || timeValue === "45") {
          matchesTime = recipe.time <= Number(timeValue);
        } else if (timeValue === "999") {
          matchesTime = recipe.time > 45;
        }

        return matchesQuery && matchesCategory && matchesDifficulty && matchesTime;
      });

      const sorted = filtered.slice().sort((a, b) => compareRecipes(a, b, sortBy));
      const total = sorted.length;
      const totalPages = Math.max(1, Math.ceil(total / searchState.pageSize));
      if (searchState.pageNo > totalPages) {
        searchState.pageNo = totalPages;
      }
      const start = (searchState.pageNo - 1) * searchState.pageSize;
      const paged = sorted.slice(start, start + searchState.pageSize);
      searchState.total = total;
      renderRecipes(paged, buildSummaryText(paged.length, total, searchState.pageNo, searchState.pageSize));
      updatePager();
    }

    [searchInput, categoryFilter, difficultyFilter, timeFilter, sortFilter].filter(Boolean).forEach((item) => {
      item.addEventListener("input", function () {
        void applyFilters(true);
      });
      item.addEventListener("change", function () {
        void applyFilters(true);
      });
    });

    if (pageSizeFilter) {
      pageSizeFilter.addEventListener("change", function () {
        searchState.pageSize = Math.max(1, Number(pageSizeFilter.value || 12));
        void applyFilters(true);
      });
    }

    if (prevPageButton) {
      prevPageButton.addEventListener("click", function () {
        if (searchState.pageNo <= 1) {
          return;
        }
        searchState.pageNo -= 1;
        void applyFilters(false);
      });
    }

    if (nextPageButton) {
      nextPageButton.addEventListener("click", function () {
        const totalPages = Math.max(1, Math.ceil((searchState.total || 0) / Math.max(1, searchState.pageSize || 1)));
        if (searchState.pageNo >= totalPages) {
          return;
        }
        searchState.pageNo += 1;
        void applyFilters(false);
      });
    }

    resetFilters.addEventListener("click", function () {
      searchInput.value = "";
      categoryFilter.value = "";
      difficultyFilter.value = "";
      timeFilter.value = "";
      if (sortFilter) {
        sortFilter.value = defaultSortBy;
      }
      if (pageSizeFilter) {
        pageSizeFilter.value = String(defaultPageSize);
      }
      searchState.pageSize = pageSizeFilter ? Number(pageSizeFilter.value) : defaultPageSize;
      void applyFilters(true);
    });

    presetButtons.forEach((button) => {
      button.addEventListener("click", function () {
        searchInput.value = button.getAttribute("data-query") || "";
        void applyFilters(true);
      });
    });

    updatePager();
    void applyFilters(true);
  }

  window.KitchenOrbit = {
    initQA,
    initSearch,
  };
})();

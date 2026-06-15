# WeCom 企微客服 AI 机器人 — 项目流程图

> **项目**：wecom-app  
> **路径**：`Backend/beckend/wecom-app`  
> **描述**：基于 Spring Boot 3.2 + Dify.ai 的企业微信智能客服机器人

---

## 1. 🏗️ 整体架构总览

```mermaid
graph TB
    subgraph "🌐 外部系统"
        WC["🏢 企业微信服务器<br/>WeCom Server"]
        Dify["🤖 Dify.ai API<br/>大模型引擎"]
    end

    subgraph "🔌 接入层"
        VC["✅ 验证控制器<br/>ValidateController<br/>GET / （URL 校验）"]
        CC["📩 回调控制器<br/>CallbackController<br/>POST / （消息回调）"]
    end

    subgraph "🔐 加解密层"
        WX["🔑 WXBizJsonMsgCrypt<br/>AES-256-CBC 加解密"]
        CDS["📄 回调解密服务<br/>CallbackDecryptService"]
    end

    subgraph "⚙️ 核心业务层"
        MS["📨 消息处理主流程<br/>MessageService<br/>@Async 异步执行"]
        S3["💬 会话状态管理<br/>SessionStateService"]
        DCS["💭 对话 ID 缓存<br/>DifyConversationService"]
        ATS["🎫 Token 管理<br/>AccessTokenService"]
        MCB["📝 消息体构造器<br/>MessageContentBuilder"]
    end

    subgraph "🔗 API 客户端层"
        WAC["📤 企微 API 客户端<br/>WecomApiClient<br/>消息/会话/Token"]
        DAC["🧠 Dify API 客户端<br/>DifyApiClient<br/>对话/回复"]
    end

    subgraph "📋 配置与基础设施"
        WP["⚙️ 配置属性<br/>WecomProperties"]
        WCfg["📦 Bean 声明<br/>WecomConfig"]
        CACHE["🗄️ 内存缓存<br/>ConcurrentHashMap<br/>Token / 对话ID / 去重"]
    end

    WC -->|"GET / 验证URL"| VC
    WC -->|"POST / 推送消息"| CC

    VC --> WX
    CC --> MS
    MS --> CDS
    CDS --> WX

    MS --> ATS
    MS --> S3
    MS --> DCS
    MS --> MCB

    MS --> WAC
    MS --> DAC

    WAC --> WC
    DAC --> Dify

    ATS --> CACHE
    DCS --> CACHE
    MS --> CACHE

    WP --> WCfg
```

---

## 2. 📂 项目结构树

```mermaid
graph TD
    ROOT["📁 wecom-app/"] --> POM["📄 pom.xml"]
    ROOT --> APP_YAML["⚙️ application.yaml"]

    ROOT --> CLIENT_PKG["client/"]
    CLIENT_PKG --> DifyClient["🧠 DifyApiClient.java"]
    CLIENT_PKG --> WecomClient["📤 WecomApiClient.java"]

    ROOT --> CONFIG_PKG["config/"]
    CONFIG_PKG --> Cfg_WecomConfig["📦 WecomConfig.java"]
    CONFIG_PKG --> Cfg_WecomProps["⚙️ WecomProperties.java"]

    ROOT --> CONST_PKG["constants/"]
    CONST_PKG --> Const_WecomConst["📌 WecomConstants.java"]

    ROOT --> CTRL_PKG["controller/"]
    CTRL_PKG --> Ctrl_Callback["📩 CallbackController.java"]
    CTRL_PKG --> Ctrl_Validate["✅ ValidateController.java"]

    ROOT --> DTO_PKG["dto/"]
    DTO_PKG --> DTO_AccessToken["🎫 AccessTokenResponse.java"]
    DTO_PKG --> DTO_CallbackMsg["📨 CallbackMessage.java"]
    DTO_PKG --> DTO_DifyReq["🤖 DifyChatRequest.java"]
    DTO_PKG --> DTO_DifyRsp["🤖 DifyChatResponse.java"]
    DTO_PKG --> DTO_SessionState["💬 GetSessionStateResponse.java"]
    DTO_PKG --> DTO_SendMsg["📤 SendMsgResponse.java"]
    DTO_PKG --> DTO_SessionReq["📋 SessionStateRequest.java"]
    DTO_PKG --> DTO_SyncMsg["📥 SyncMsgResponse.java"]
    DTO_PKG --> DTO_TransSession["🔄 TransSessionStateResponse.java"]
    DTO_PKG --> DTO_BaseRsp["📦 WecomBaseResponse.java"]

    ROOT --> SVC_PKG["service/"]
    SVC_PKG --> Svc_AccessToken["🎫 AccessTokenService.java"]
    SVC_PKG --> Svc_CallbackDecrypt["🔐 CallbackDecryptService.java"]
    SVC_PKG --> Svc_DifyConv["💭 DifyConversationService.java"]
    SVC_PKG --> Svc_MsgBuilder["📝 MessageContentBuilder.java"]
    SVC_PKG --> Svc_Message["📨 MessageService.java"]
    SVC_PKG --> Svc_SessionState["💬 SessionStateService.java"]

    ROOT --> CRYPTO_PKG["util/wx/mp/aes/"]
    CRYPTO_PKG --> Util_AesEx["⚠️ AesException.java"]
    CRYPTO_PKG --> Util_ByteGrp["📦 ByteGroup.java"]
    CRYPTO_PKG --> Util_JsonParse["📄 JsonParse.java"]
    CRYPTO_PKG --> Util_PKCS7["🔢 PKCS7Encoder.java"]
    CRYPTO_PKG --> Util_SHA1["🔏 SHA1.java"]
    CRYPTO_PKG --> Util_WXBiz["🔐 WXBizJsonMsgCrypt.java"]

    ROOT --> MAIN["🚀 WecomAppApplication.java"]
```

---

## 3. 📨 消息处理主流程（核心）

```mermaid
sequenceDiagram
    participant WC as 🏢 企微服务器
    participant CC as 📩 CallbackController
    participant MS as ⚙️ MessageService
    participant CDS as 🔐 CallbackDecryptService
    participant WX as 🔑 WXBizJsonMsgCrypt
    participant ATS as 🎫 AccessTokenService
    participant WAC as 📤 WecomApiClient
    participant S3 as 💬 SessionStateService
    participant DAC as 🧠 DifyApiClient
    participant DCS as 💭 DifyConversationService
    participant MCB as 📝 MessageContentBuilder

    rect rgb(230, 245, 255)
        Note over WC,CC: ① 企业微信 URL 验证（首次配置时）
        WC->>+CC: GET /?msg_signature&timestamp&nonce&echostr
        CC->>+WX: VerifyURL(msg_signature, timestamp, nonce, echostr)
        WX-->>-CC: 解密后的 echostr
        CC-->>-WC: 返回明文 echostr ✅ 验证通过
    end

    rect rgb(255, 245, 230)
        Note over WC,MS: ② 消息回调处理（核心流程）

        WC->>+CC: POST /（加密 XML + 签名参数）
        CC->>+MS: handleMessage() [@Async 异步执行]
        CC-->>WC: 立即返回空字符串（防 5 秒超时）
        Note over MS: --- 步骤 2a：解密回调消息 ---
        MS->>+CDS: decrypt(encryptedXml)
        CDS->>+WX: DecryptMsg(signature, timestamp, nonce, postData)
        WX-->>-CDS: 解密后的 JSON
        CDS-->>-MS: CallbackMessage{openKfid, token}

        Note over MS: --- 步骤 2b：消息去重 ---
        Note over MS: 基于 ConcurrentHashMap.newKeySet()<br/>用 callback token 去重

        Note over MS: --- 步骤 2c：获取 Access Token ---
        MS->>+ATS: getAccessToken()
        ATS-->>-MS: access_token

        Note over MS: --- 步骤 2d：分页拉取消息 ---
        loop has_more == true（持续分页直到拉完）
            MS->>+WAC: syncMsg(token, openKfid, cursor)
            WAC->>+WC: POST /cgi-bin/kf/sync_msg
            WC-->>-WAC: SyncMsgResponse{msg_list, next_cursor, has_more}
            WAC-->>-MS: SyncMsgResponse
        end

        Note over MS: --- 步骤 2e：查找最后一条用户消息 ---
        Note over MS: 从 msg_list 末尾往前遍历<br/>找 origin == 3（微信用户发送的消息）

        Note over MS: --- 步骤 2f：检查/切换会话状态 ---
        MS->>+S3: getSessionState(openKfid, externalUserid)
        S3->>+WAC: getSessionState(openKfid, externalUserid)
        WAC->>+WC: POST /cgi-bin/kf/service_state/get
        WC-->>-WAC: service_state
        WAC-->>-S3: service_state
        S3-->>-MS: service_state

        alt 当前状态不是机器人服务（BOT_SERVICE）
            MS->>+S3: transferToBotService(openKfid, externalUserid)
            S3->>+WAC: transSessionState(openKfid, externalUserid, 1)
            WAC->>+WC: POST /cgi-bin/kf/service_state/trans
            WC-->>-WAC: ✅ 成功
            WAC-->>-S3: ✅ 成功
            S3-->>-MS: ✅ 成功
        end

        Note over MS: --- 步骤 2g：发送回复 ---
        alt 消息类型 == TEXT（文本）
            Note over MS: 🤖 Dify AI 智能回复
            MS->>+DCS: getConversationId(externalUserid)
            DCS-->>-MS: conversation_id（或 null 表示新对话）
            MS->>+DAC: chatMessage(query, user, conversationId)
            DAC->>+Dify: POST /chat-messages
            Dify-->>-DAC: DifyChatResponse{answer, conversationId}
            DAC-->>-MS: AI 生成的回答
            MS->>+DCS: saveConversationId(externalUserid, conversationId)
            DCS-->>-MS: 已保存（支持多轮对话）
            MS->>+WAC: sendMsg(openKfid, userid, "text", AI回答)
            WAC->>+WC: POST /cgi-bin/kf/send_msg
            WC-->>-WAC: 回复已发送 ✅
        else 非文本消息（图片/语音/视频/文件/链接/小程序）
            Note over MS: 原样转发给用户
            MS->>+MCB: build(msgtype, content)
            MCB-->>-MS: 构造好的 JSON 请求体
            MS->>+WAC: sendMsg(openKfid, userid, msgtype, content)
            WAC->>+WC: POST /cgi-bin/kf/send_msg
            WC-->>-WAC: 转发成功 ✅
        end
    end
```

---

## 4. 🔄 会话状态机

```mermaid
stateDiagram-v2
    state "0️⃣ 未处理<br/>UNTREATED" as UNTREATED
    state "1️⃣ 机器人服务<br/>BOT_SERVICE" as BOT_SERVICE
    state "2️⃣ 排队等待<br/>WAITING" as WAITING
    state "3️⃣ 人工服务<br/>MANUAL_SERVICE" as MANUAL_SERVICE
    state "4️⃣ 已结束<br/>FINISHED" as FINISHED

    [*] --> UNTREATED: 新消息到达
    UNTREATED --> BOT_SERVICE: transferToBotService()<br/>自动转入机器人
    UNTREATED --> WAITING: transferToWaitingPool()<br/>转入排队

    BOT_SERVICE --> MANUAL_SERVICE: transferToManualService()<br/>转人工客服
    BOT_SERVICE --> FINISHED: finishSession()<br/>结束会话

    WAITING --> MANUAL_SERVICE: 客服接听
    WAITING --> FINISHED: 超时关闭

    MANUAL_SERVICE --> FINISHED: finishSession()<br/>结束会话

    FINISHED --> [*]: 会话结束
```

---

## 5. 💭 Dify 多轮对话管理

```mermaid
flowchart LR
    subgraph "🗄️ DifyConversationService 内存缓存"
        direction LR
        USER1["👤 用户A<br/>external_userid_01"] --> CID1["abc123"]
        USER2["👤 用户B<br/>external_userid_02"] --> CID2["def456"]
        USER3["👤 用户C<br/>external_userid_03"] --> CID3["ghi789"]
    end

    subgraph "🔄 多轮对话流程"
        direction TB
        A["💬 用户发消息"]
        B["🔍 getConversationId()<br/>查缓存"]
        C["✅ 有 conversation_id<br/>→ 继续对话"]
        D["❌ 无 conversation_id<br/>→ 开启新对话"]
        E["🌐 请求 Dify API<br/>（query + conversation_id）"]
        F["💾 saveConversationId()<br/>保存返回的 ID"]
        G["📤 AI 回答 → 发送给用户"]

        A --> B
        B --> C
        B --> D
        C --> E
        D --> E
        E --> F
        F --> G
    end
```

---

## 6. 📊 消息类型处理矩阵

```mermaid
graph LR
    subgraph "📥 企微消息类型"
        T1["📝 text<br/>文本"]
        T2["🖼️ image<br/>图片"]
        T3["🎤 voice<br/>语音"]
        T4["🎬 video<br/>视频"]
        T5["📎 file<br/>文件"]
        T6["🔗 link<br/>链接"]
        T7["📱 miniprogram<br/>小程序卡片"]
    end

    subgraph "⚡ 处理策略"
        H1["🤖 Dify AI 生成回复"]
        H2["📨 原样转发给用户<br/>MessageContentBuilder"]
    end

    T1 --> H1
    T2 --> H2
    T3 --> H2
    T4 --> H2
    T5 --> H2
    T6 --> H2
    T7 --> H2
```

---

## 7. 🎫 Access Token 生命周期

```mermaid
flowchart LR
    subgraph "🔄 AccessTokenService 缓存管理"
        direction TB
        S1["🚀 应用启动"] --> S2["❌ token = null"]
        S2 --> S3["📞 首次 getAccessToken()"]
        S3 --> S4["🌐 GET /gettoken<br/>?corpid=...&corpsecret=..."]
        S4 --> S5["📋 解析 access_token<br/>+ expires_in"]
        S5 --> S6["💾 缓存到内存<br/>记录过期时间"]
        S6 --> S7["✅ 返回 token"]

        S7 --> S8{"⏰ 是否过期？<br/>（提前 5 分钟刷新）"}
        S8 -->|"未过期 ✅"| S7
        S8 -->|"快过期 ⏳"| S4
    end
```

---

## 8. 🔐 回调加解密流程

```mermaid
sequenceDiagram
    participant WC as 🏢 企微服务器
    participant CTRL as 📩 Controller
    participant WX as 🔑 WXBizJsonMsgCrypt
    participant CDS as 🔐 CallbackDecryptService

    Note over WC,CDS: 加密发送（企微端）
    WC->>WX: 企业微信使用 EncodingAESKey<br/>对 JSON 消息体进行 AES-256-CBC 加密
    WC->>CTRL: POST /<br/>?msg_signature=SHA1(token, ts, nonce, encrypt)<br/>&timestamp=...&nonce=...<br/>Body: {"encrypt":"Base64(AES(json))"}

    Note over CTRL,CDS: 解密流程（应用端）
    CTRL->>+CDS: decrypt(postData, msgSignature, timestamp, nonce)
    CDS->>+WX: DecryptMsg(msgSignature, timestamp, nonce, postData)
    WX->>WX: ① 校验签名：<br/>SHA1(token, ts, nonce, encrypt) == msg_signature?
    WX->>WX: ② Base64 解码 encrypt 字段
    WX->>WX: ③ AES-256-CBC 解密
    WX->>WX: ④ 去除 PKCS7 填充
    WX->>WX: ⑤ 提取明文 JSON
    WX-->>-CDS: 解密后的明文消息
    CDS->>CDS: 提取 openKfid + token
    CDS-->>-CTRL: CallbackMessage
```

---

## 9. 🔗 组件依赖关系

```mermaid
graph TD
    subgraph "📦 注入依赖链"
        CC["📩 CallbackController"] -->|"注入"| MS["⚙️ MessageService"]
        MS -->|"注入"| CDS["🔐 CallbackDecryptService"]
        MS -->|"注入"| ATS["🎫 AccessTokenService"]
        MS -->|"注入"| WAC["📤 WecomApiClient"]
        MS -->|"注入"| DAC["🧠 DifyApiClient"]
        MS -->|"注入"| S3["💬 SessionStateService"]
        MS -->|"注入"| DCS["💭 DifyConversationService"]
        MS -->|"注入"| MCB["📝 MessageContentBuilder"]
        ATS -->|"使用"| RC1["🌐 RestClient"]
        WAC -->|"使用"| RC2["🌐 RestClient"]
        DAC -->|"使用"| RC3["🌐 RestClient"]
        CDS -->|"使用"| WX["🔑 WXBizJsonMsgCrypt"]
        S3 -->|"使用"| WAC
        VC["✅ ValidateController"] -->|"使用"| WX
    end

    subgraph "📋 Spring 配置声明"
        WCFG["📦 WecomConfig<br/>@Configuration"] -->|"声明 Bean"| WXBEAN["🔑 WXBizJsonMsgCrypt"]
        WCFG -->|"声明 Bean"| RCBEAN["🌐 RestClient"]
        WP["⚙️ WecomProperties<br/>@ConfigurationProperties"] -->|"绑定"| YAML["📄 application.yaml"]
    end
```

---

## 10. 📅 启动与调用时序

```mermaid
timeline
    title 🚀 应用启动 → 处理第一条消息
    section 1️⃣ 启动阶段
        🚀 main : @SpringBootApplication + @EnableAsync
        📦 WecomConfig : 初始化 WXBizJsonMsgCrypt + RestClient
        ⚙️ WecomProperties : 绑定 wecom.* 配置项
        🎫 AccessTokenService : 就绪（懒加载）
    section 2️⃣ URL 验证阶段
        ✅ GET / : 企微验证回调 URL 有效性
        🔐 ValidateController : VerifyURL() 解密 echostr 并返回
    section 3️⃣ 消息处理阶段
        📩 POST / : 用户消息到达
        ⚙️ CallbackController : 异步调用 handleMessage()
        📨 MessageService : 解密 → 去重 → 拉消息 → AI 回复

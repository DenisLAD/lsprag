# 🔐 GigaChat Authentication Guide

## Обзор

Плагин Reasoning Test Generator поддерживает **три метода аутентификации** в GigaChat API:

1. **API Key** - для физических лиц
2. **Client Credentials** - для юридических лиц (бизнес)
3. **Certificate (JKS/PKCS12)** - для корпоративных клиентов с сертификатами

---

## 📋 Методы аутентификации

### 1. API Key (для физических лиц)

**Настройка:**
```
Settings → Tools → Reasoning Test Generator

LLM Provider: GIGACHAT
Auth Method: API_KEY
API Key: <ваш API ключ>
```

**OAuth Flow:**
```
1. POST https://ngw.devices.sberbank.ru:9443/api/v2/oauth
   Headers:
     Authorization: Bearer {apiKey}
     Content-Type: application/x-www-form-urlencoded
     RqUID: {UUID}
   Body: scope=GIGACHAT_API_PERS

2. Response: {"access_token": "...", "expires_at": 1234567890}

3. POST https://gigachat.devices.sberbank.ru/api/v1/chat/completions
   Headers:
     Authorization: Bearer {access_token}
   Body: {"model": "GigaChat-Max", "messages": [...]}
```

**Преимущества:**
- ✅ Простая настройка
- ✅ Не требует сертификатов
- ✅ Подходит для индивидуальных разработчиков

**Недостатки:**
- ❌ Ограничения по количеству запросов
- ❌ Только для физических лиц

---

### 2. Client Credentials (для бизнеса)

**Настройка:**
```
Settings → Tools → Reasoning Test Generator

LLM Provider: GIGACHAT
Auth Method: CLIENT_CREDENTIALS
Client ID: <ваш client_id>
Client Secret: <ваш client_secret>
Scope: GIGACHAT_API_B2B или GIGACHAT_API_CORP
```

**OAuth Flow:**
```
1. POST https://ngw.devices.sberbank.ru:9443/api/v2/oauth
   Headers:
     Authorization: Basic {base64(clientId:clientSecret)}
     Content-Type: application/x-www-form-urlencoded
     RqUID: {UUID}
   Body: scope=GIGACHAT_API_B2B

2. Response: {"access_token": "...", "expires_at": 1234567890}

3. Выбор endpoint по scope:
   - GIGACHAT_API_CORP → https://api.giga.chat/v1/chat/completions
   - иначе → https://gigachat.devices.sberbank.ru/api/v1/chat/completions
```

**Преимущества:**
- ✅ Выше лимиты запросов
- ✅ Для юридических лиц
- ✅ Разные scope (B2B, CORP)

**Недостатки:**
- ❌ Требует регистрации приложения
- ❌ Нужно хранить client secret

---

### 3. Certificate (JKS/PKCS12) - БЕЗ API КЛЮЧА ⭐

**Настройка:**
```
Settings → Tools → Reasoning Test Generator

LLM Provider: GIGACHAT
Auth Method: CERTIFICATE
Use SSL with JKS keystore: ✓
KeyStore Type: JKS или PKCS12
KeyStore Path: /path/to/keystore.jks
KeyStore Password: <пароль keystore>
Client ID: <ваш client_id>
Client Secret: <ваш client_secret>
Scope: GIGACHAT_API_CORP
```

**OAuth Flow с сертификатами:**
```
1. Загрузка JKS/PKCS12 keystore с клиентскими сертификатами
   KeyStore keyStore = KeyStore.getInstance(keystoreType);
   keyStore.load(new FileInputStream(keystorePath), password);

2. Инициализация KeyManager (клиентские сертификаты)
   KeyManagerFactory kmf = KeyManagerFactory.getInstance(...);
   kmf.init(keyStore, password);

3. Инициализация TrustManager (доверенные CA)
   TrustManagerFactory tmf = TrustManagerFactory.getInstance(...);
   tmf.init(keyStore);

4. Создание SSLContext с mutual TLS
   SSLContext sslContext = SSLContext.getInstance("TLS");
   sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

5. POST https://ngw.devices.sberbank.ru:9443/api/v2/oauth
   SSL Client Certificate: {сертификат из keystore}
   Headers:
     Authorization: Basic {base64(clientId:clientSecret)}
     Content-Type: application/x-www-form-urlencoded
   Body: scope=GIGACHAT_API_CORP

6. Response: {"access_token": "...", "expires_at": 1234567890}

7. POST https://api.giga.chat/v1/chat/completions
   SSL Client Certificate: {сертификат из keystore}
   Headers:
     Authorization: Bearer {access_token}
   Body: {"model": "GigaChat-Max", "messages": [...]}
```

**Преимущества:**
- ✅ **Не требуется API Key** - аутентификация по сертификату
- ✅ Maximum security - mutual TLS (mTLS)
- ✅ Для корпоративных клиентов
- ✅ Highest trust level
- ✅ Обходит некоторые ограничения безопасности

**Недостатки:**
- ❌ Требует получения сертификатов
- ❌ Нужно хранить keystore файл
- ❌ Сложнее настройка

---

## 🔑 Получение сертификатов для GigaChat

### Шаг 1: Регистрация приложения

1. Перейдите в [GigaChat Developer Portal](https://developers.sber.ru/gigachat)
2. Создайте новое приложение
3. Выберите тип аутентификации: **Сертификаты**
4. Скачайте корневой сертификат GigaChat

### Шаг 2: Создание клиентского сертификата

```bash
# 1. Создать приватный ключ
openssl genrsa -out client.key 2048

# 2. Создать CSR (Certificate Signing Request)
openssl req -new -key client.key -out client.csr \
  -subj "/CN=your-client-id/O=Your Organization/C=RU"

# 3. Отправить CSR в GigaChat для подписи
# (через Developer Portal или поддержку)

# 4. Получить подписанный сертификат
# (файл client.crt от GigaChat)
```

### Шаг 3: Создание JKS keystore

```bash
# 1. Конвертировать в PKCS12
openssl pkcs12 -export \
  -in client.crt \
  -inkey client.key \
  -out keystore.pkcs12 \
  -name "gigachat-client"

# 2. Конвертировать в JKS (если нужно)
keytool -importkeystore \
  -srckeystore keystore.pkcs12 \
  -srcstoretype PKCS12 \
  -destkeystore keystore.jks \
  -deststoretype JKS
```

### Шаг 4: Импорт корневого сертификата GigaChat

```bash
# Импортировать корневой сертификат GigaChat
keytool -importcert \
  -file gigachat-root-ca.crt \
  -keystore keystore.jks \
  -alias gigachat-root
```

---

## ⚙️ Настройка в плагине

### Для API Key

1. Откройте `Settings → Tools → Reasoning Test Generator`
2. Выберите `LLM Provider: GIGACHAT`
3. Выберите `Auth Method: API_KEY`
4. Введите ваш API ключ
5. Нажмите `OK`

### Для Client Credentials

1. Откройте `Settings → Tools → Reasoning Test Generator`
2. Выберите `LLM Provider: GIGACHAT`
3. Выберите `Auth Method: CLIENT_CREDENTIALS`
4. Введите `Client ID` и `Client Secret`
5. Выберите `Scope`:
   - `GIGACHAT_API_B2B` - для бизнеса
   - `GIGACHAT_API_CORP` - для корпораций
6. Нажмите `OK`

### Для Certificate (без API ключа) ⭐

1. Откройте `Settings → Tools → Reasoning Test Generator`
2. Выберите `LLM Provider: GIGACHAT`
3. Выберите `Auth Method: CERTIFICATE`
4. Включите `Use SSL with JKS keystore`
5. Выберите `KeyStore Type`: `JKS` или `PKCS12`
6. Укажите путь к keystore файлу
7. Введите пароль keystore
8. Введите `Client ID` и `Client Secret`
9. Выберите `Scope: GIGACHAT_API_CORP`
10. Нажмите `OK`

---

## 🔍 Troubleshooting

### Ошибка: "SSL: CERTIFICATE_VERIFY_FAILED"

**Причина:** Не импортирован корневой сертификат GigaChat

**Решение:**
```bash
keytool -importcert \
  -file gigachat-root-ca.crt \
  -keystore keystore.jks \
  -alias gigachat-root
```

---

### Ошибка: "401 Unauthorized"

**Причина:** Токен истек или неверные credentials

**Решение:**
- Проверьте что `Client ID` и `Client Secret` верные
- Убедитесь что сертификат действителен
- Проверьте что scope соответствует типу аутентификации

---

### Ошибка: "Keystore was tampered with or password is incorrect"

**Причина:** Неверный пароль keystore

**Решение:**
- Проверьте пароль keystore
- Пересоздайте keystore если пароль утерян

---

## 📊 Сравнение методов

| Метод | API Key | Сертификаты | Клиенты |
|-------|---------|-------------|---------|
| **Простота** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Безопасность** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Лимиты** | Стандартные | Повышенные | Повышенные |
| **Для кого** | Физлица | Корпорации | Бизнес |

---

## ✅ Проверка работы

### Тестовый запрос

```bash
# 1. Получить токен
curl -X POST "https://ngw.devices.sberbank.ru:9443/api/v2/oauth" \
  -H "Authorization: Bearer {apiKey}" \
  -H "RqUID: $(uuidgen)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "scope=GIGACHAT_API_PERS"

# 2. Использовать токен
curl -X POST "https://gigachat.devices.sberbank.ru/api/v1/chat/completions" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat-Max",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

---

## 📖 Ссылки

- [GigaChat Developer Portal](https://developers.sber.ru/gigachat)
- [GigaChat API Documentation](https://developers.sber.ru/docs/gigachat)
- [OAuth 2.0 Specification](https://oauth.net/2/)
- [mTLS Best Practices](https://tools.ietf.org/html/rfc7525)

---

**GigaChat поддерживает аутентификацию по сертификатам БЕЗ API ключа!** 🔐

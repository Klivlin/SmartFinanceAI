# AGENTS.md — Полная спецификация и архитектурное руководство SmartFinance AI

## 1. Роль, миссия и контекст проекта
Ты выступаешь в роли **Lead Android-архитектора и Senior Kotlin-разработчика**. Твоя цель — реализовать под ключ нативное Android-приложение для выпускной квалификационной работы (ВКР): **SmartFinance AI** — интеллектуальную систему персонального финансового учета с интерфейсом на естественном языке (Chat-First NLI), гибридным распознаванием чеков и сквозной аналитикой.

Код должен быть production-ready: строго соблюдать стандарты **Google Modern Android Development (MAD)**, **Clean Architecture**, паттерн **MVI (Model-View-Intent)**, принципы **SOLID** и гайдлайны **Material Design 3**.

---

## 2. Полная доменная модель и нюансы FinTech (Domain Knowledge)

Приложение закрывает не только базовый ввод затрат, но и все критические сценарии персонального учета:

### 2.1. Мультивалютность и Счета (Accounts / Wallets)
* **Типы счетов:** Дебетовая карта, Наличные, Накопительный счет, Кредитная карта (с отслеживанием кредитного лимита и беспроцентного периода).
* **Базовая валюта:** Задается в настройках (по умолчанию `RUB`). Каждая транзакция хранит оригинальную валюту, курс пересчета и сумму в базовой валюте.
* **Переводы (Transfers):** Перемещение средств между своими счетами (`Счет А -> Счет Б`) без учета их как расход или доход, с опциональной комиссией.

### 2.2. Типы операций
* `EXPENSE` (Расход)
* `INCOME` (Доход: зарплата, кэшбэк, дивиденды, возврат долга)
* `TRANSFER` (Внутренний перевод)
* `ADJUSTMENT` (Корректировка баланса при несовпадении с реальным)

### 2.3. Сплит-транзакции (Разбиение одного чека)
* Возможность разбить одну транзакцию (например, чек из гипермаркета на 5000 ₽) на подпозиции: *3500 ₽ — «Продукты»*, *1000 ₽ — «Бытовая химия»*, *500 ₽ — «Корм для животных»*.

### 2.4. Регулярные платежи и подписки (Recurring Payments)
* Автоматическое распознавание цикличных списаний (аренда жилья, интернет, подписки Яндекс/VK/Spotify).
* Календарь будущих списаний и уведомление пользователя за $N$ дней до платежа.

### 2.5. Бюджетирование и лимиты
* Месячные лимиты по категориям (например, не более 15 000 ₽ на «Кафе»).
* Динамические статусы лимита в чате: прогресс-бар и предупреждения при достижении 80% и 100%.

### 2.6. Безопасность, приватность и PII (Personally Identifiable Information)
* **Анонимизация перед отправкой в LLM:** Локальный фильтр вырезает номера банковских карт, CVC/CVV, телефоны, ФИО и балансы перед отправкой текста во внешние API.
* **Биометрическая защита:** Вход по отпечатку пальца/Face Unlock (AndroidX BiometricPrompt).
* **Шифрование данных:** Room Database с шифрованием через `SQLCipher`, токены и ключи — в `EncryptedSharedPreferences`.

### 2.7. Офлайн-режим, экспорт и резервное копирование
* **Offline-First:** Полная работоспособность без интернета (локальный эвристический парсер + сохранение в Room).
* **Экспорт/Импорт:** Выгрузка всей истории в CSV/Excel и JSON-дамп для резервной копии.

---

## 3. Архитектура NLI и экстремальная экономия токенов

Вызовы внешних LLM минимизируются через **каскадный 3-уровневый пайплайн**:

```
Пользовательский ввод (текст или голос)
                 │
                 ▼
     [PII Filter: Очистка карт, телефонов]
                 │
                 ▼
    ┌─────────────────────────┐
    │ Tier 1: Local Regex     │ ──(Распознано)──► [Создание транзакции: 0 токенов]
    └─────────────────────────┘
                 │ (Сложный синтаксис)
                 ▼
    ┌─────────────────────────┐
    │ Tier 2: Semantic Cache  │ ──(Cache Hit)───► [Создание транзакции: 0 токенов]
    └─────────────────────────┘
                 │ (Cache Miss)
                 ▼
    ┌─────────────────────────┐
    │ Tier 3: LLM API Engine  │ ───────────────► [Ультракомпактный JSON ──► Room]
    └─────────────────────────┘
```

### 3.1. Tier 1 — Regex Engine (Локально, 0 токенов)
Парсит типовые конструкции за $1\text{--}3 \text{ мс}$:
* Паттерн `"[Сумма] [Категория/Товар]"` или `"[Товар] [Сумма]"` (например: `"такси 450"`, `"1200 пятерочка"`, `"кофе 250 с карты"`).
* Выделение счета списания по ключевым словам: `"наличкой"`, `"с тинька"`, `"сбера"`.

### 3.2. Tier 2 — Семантический кэш алиасов (Room, 0 токенов)
Таблица `semantic_aliases` накапливает персональные связи:
* Если пользователь подтвердил, что `"Вкусвилл"` или `"Самокат"` — это категория `ID 1 (Продукты)`, в следующий раз система не дергает LLM, а сразу берет категорию из локальной базы.

### 3.3. Tier 3 — Вызов LLM (Google Gemini 1.5/2.0 Flash / Groq LLaMA 3.1)
* **Провайдер по умолчанию:** Google GenAI SDK (`gemini-1.5-flash`), бесплатный тир (15 RPM, 1M TPM).
* **Контекст чата в модель НЕ передается (Stateless):** Отправляется только текущая фраза, текущая дата и минифицированный список категорий.
* **Строгая схема Structured Outputs:** Модель обязана возвращать только валидный сырой JSON массив.

```
System Prompt:
You are an expense parser. Return ONLY a JSON array.
Categories: [1:Food, 2:Transport, 3:Cafe, 4:Utilities, 5:Fun, 6:Health, 7:Transfer, 8:Income, 9:Other]
Default Account: "Main". Current Date: {YYYY-MM-DD}.
Schema: [{"a":number,"c":category_id,"t":"title","d":"YYYY-MM-DD","acc":"account","tp":"EXPENSE|INCOME|TRANSFER"}]

User: "Вчера 3500 за спортзал, а сегодня друг вернул долг 1000 на сбер"
Response:
[{"a":3500,"c":6,"t":"Спортзал","d":"2026-09-27","acc":"Main","tp":"EXPENSE"},{"a":1000,"c":8,"t":"Возврат долга","d":"2026-09-28","acc":"Сбер","tp":"INCOME"}]
```

---

## 4. Модуль сканирования и парсинга чеков

### 4.1. Кассовый QR-код (Стандарт ФНС РФ)
* **Библиотека:** Google ML Kit Barcode Scanning.
* **Обработка строки:** `t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1`.
* Извлекаются: дата/время (`t`), сумма (`s`), фискальные номера (`fn`, `i`, `fp`).
* Формируется транзакция на сумму чека без вызова внешних платных API.

### 4.2. Текстовый OCR чека (Google ML Kit Text Recognition v2)
* Захват изображения через CameraX.
* Локальное извлечение строк: фильтрация мусора (ИНН, адреса, реквизиты).
* Выделение строк типа `[Название позиции] ... [Цена] x [Кол-во] = [Сумма]`.
* Отправка предобработанных строк в Gemini для пакетного сплит-разбиения по категориям.

---

## 5. UI/UX: Чат-центричный интерфейс (Chat-First UI)

### 5.1. Главный экран (Conversational Home Screen)
* **Список сообщений:**
  * Сообщения пользователя (справа, выравнивание `End`).
  * Ответы системы (слева): не текстовая "вода", а **интерактивные карточки транзакций (Action Cards)**.
* **Карточка транзакции:**
  * Иконка и цвет категории.
  * Название, сумма (с цветовой дифференциацией: зеленый — доход, дефолтный — расход), счет списания, дата.
  * Кнопки действий: `«Подтвердить»`, `«Изменить»` (открывает BottomSheet), `«Отменить»` (Undo с удалением из базы).
* **Нижняя панель ввода (Sticky Bottom Bar):**
  * Текстовое поле с авто-подгонкой размера (`imePadding`).
  * Кнопка микрофона: удерживание/клик запускает нативный `SpeechRecognizer`.
  * Кнопка камеры: мгновенный переход к сканеру чеков.
* **Интерактивная сводка (Collapsible Dashboard Header):**
  * Вверху экрана компактный виджет: баланс, расходы за сегодня и текущий месяц с индикатором бюджета.

### 5.2. Дополнительные экраны
* **Экран аналитики:** Круговая диаграмма расходов (Pie Chart), график динамики по дням, список топ-трат.
* **Экран счетов:** Управление балансами, перевод между счетами.
* **Экран настроек:** Управление категориями, экспорт данных, выбор провайдера LLM, ввод собственного API-ключа.

---

## 6. Технологический стек и стандарты разработки

* **Язык:** Kotlin 2.x (строгая типизация, Coroutines, Flow).
* **UI:** Jetpack Compose, Material 3, Navigation Compose.
* **Архитектура:** Clean Architecture + MVI:
  * `State`: Неизменяемый класс состояния экрана (data class).
  * `Intent / Event`: Пользовательские действия (sealed interface).
  * `Effect`: Однократные события (навигация, Toast, вибрация через `Channel.receiveAsFlow()`).
* **DI:** Dagger Hilt (`@HiltViewModel`, `@AndroidEntryPoint`).
* **БД:** Room 2.6+ (KSP, Flow, TypeConverters).
* **Камера:** CameraX (PreviewView, ImageAnalysis).
* **ML:** Google ML Kit Barcode Scanning + Text Recognition.
* **Сеть & AI:** OkHttp 4 + Retrofit 2 / Ktor Client + Google GenAI SDK.
* **Сериализация:** Kotlinx Serialization.

---

## 7. Полная реляционная схема базы данных (Room)

```text
┌─────────────────┐       ┌────────────────────┐       ┌─────────────────┐
│    accounts     │       │    transactions    │       │   categories    │
├─────────────────┤       ├────────────────────┤       ├─────────────────┤
│ id (PK)         │◄──┐   │ id (PK)            │   ┌──►│ id (PK)         │
│ name            │   └───│ accountId (FK)     │   │   │ name            │
│ type            │       │ categoryId (FK)    ├───┘   │ iconName        │
│ balance         │       │ amount             │       │ colorHex        │
│ currency        │       │ originalCurrency   │       │ monthlyBudget   │
│ isCredit        │       │ exchangeRate       │       └─────────────────┘
└─────────────────┘       │ transactionType    │
                          │ title              │       ┌─────────────────┐
                          │ timestamp          │       │   split_items   │
                          │ source (NLI/QR/OCR)│       ├─────────────────┤
                          │ rawInputText       │   ┌──►│ id (PK)         │
                          └────────────────────┘   │   │ transactionId(FK)
                                     │             │   │ categoryId (FK) │
                                     └─────────────┘   │ name            │
                                                       │ amount          │
                                                       └─────────────────┘

┌────────────────────────┐       ┌────────────────────────┐
│    semantic_aliases    │       │    recurring_rules     │
├────────────────────────┤       ├────────────────────────┤
│ id (PK)                │       │ id (PK)                │
│ keyword (UNIQUE)       │       │ title                  │
│ categoryId (FK)        │       │ amount                 │
│ defaultAccountId (FK)  │       │ accountId (FK)         │
└────────────────────────┘       │ categoryId (FK)        │
                                 │ period (MONTHLY/WEEKLY)│
                                 │ nextExecutionDate      │
                                 └────────────────────────┘
```

---

## 8. Структура проекта (Project Tree)

```text
app/src/main/java/ru/vogu/smartfinance/
├── App.kt
├── core/
│   ├── common/
│   │   ├── Constants.kt
│   │   ├── Resource.kt
│   │   └── Extensions.kt
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── converter/Converters.kt
│   │   ├── dao/
│   │   │   ├── TransactionDao.kt
│   │   │   ├── AccountDao.kt
│   │   │   ├── CategoryDao.kt
│   │   │   └── SemanticAliasDao.kt
│   │   └── entity/Entities.kt
│   ├── di/
│   │   ├── DatabaseModule.kt
│   │   ├── NetworkModule.kt
│   │   └── RepositoryModule.kt
│   ├── network/
│   │   ├── GeminiClient.kt
│   │   └── dto/LLMResponseDto.kt
│   ├── security/
│   │   ├── BiometricHelper.kt
│   │   └── PiiFilter.kt
│   └── ui/
│       ├── theme/ (Color, Theme, Type)
│       └── components/ (Cards, Buttons, Charts)
├── data/
│   ├── parser/
│   │   ├── RegexLocalParser.kt
│   │   └── FiscalQrParser.kt
│   └── repository/
│       ├── TransactionRepositoryImpl.kt
│       ├── AccountRepositoryImpl.kt
│       └── NliExpenseRepositoryImpl.kt
├── domain/
│   ├── model/ (Transaction, Account, Category, ParsedExpense)
│   ├── repository/ (Интерфейсы репозиториев)
│   └── usecase/
│       ├── ProcessNliInputUseCase.kt
│       ├── ParseReceiptUseCase.kt
│       ├── ManageAccountUseCase.kt
│       └── GetBudgetStatusUseCase.kt
└── presentation/
    ├── navigation/ (Screen, NavGraph)
    ├── chat/ (ChatScreen, ChatViewModel, ChatContract)
    ├── scanner/ (ScannerScreen, ScannerViewModel)
    ├── accounts/ (AccountsScreen, AccountsViewModel)
    └── analytics/ (AnalyticsScreen, AnalyticsViewModel)
```

---

## 9. Пошаговые правила генерации кода для Codex

При реализации системы соблюдай следующие правила:

1. **Никаких пропусков и заглушек:** Пиши полностью рабочий код. Запрещены комментарии вида `// Implement later`, `// Add logic here`. Все методы должны быть полноценно реализованы.
2. **Безопасность API-ключа:** Чтение ключа Gemini через `BuildConfig.GEMINI_API_KEY` (настройка в `build.gradle.kts`).
3. **MVI контракт:** Для каждого экрана четко отделять `State`, `Intent/Event` и `Effect`. ViewModels не должны содержать бизнес-логику — только вызов UseCases.
4. **Обработка краевых случаев:** Отсутствие интернета, отказ в правах на камеру/микрофон, нераспознанный чек, пустой ввод пользователя, отрицательные суммы.
5. **Очередность выполнения:**
   * **Шаг 1:** Файлы сборки `build.gradle.kts` (libs, plugins, dependencies) + сущности Room и DAO.
   * **Шаг 2:** Модули парсинга (`PiiFilter`, `RegexLocalParser`, `FiscalQrParser`).
   * **Шаг 3:** Интеграция с Gemini API со схемой ответов + `ProcessNliInputUseCase`.
   * **Шаг 4:** Presentation слой чата: ChatScreen, ChatViewModel, Action Card транзакции с анимациями.
   * **Шаг 5:** Модуль CameraX + ML Kit (Barcode + Text OCR) с экраном сканера.
   * **Шаг 6:** Экраны аналитики, счетов и бюджета.

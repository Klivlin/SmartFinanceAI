# SmartFinance AI

Нативное Android-приложение для персонального финансового учёта с интерфейсом на естественном языке (Chat-First NLI), гибридным распознаванием чеков и сквозной аналитикой. Выполнено в рамках выпускной квалификационной работы (ВКР).

## Возможности

- **Чат-центричный ввод трат**: «такси 450», «кофе 250 с карты» — без форм и календарей
- **Каскадный NLI-пайплайн** с экономией токенов: локальный regex-парсер → семантический кэш алиасов → Gemini API (в таком порядке, большинство операций обрабатывается бесплатно и локально)
- **Сканирование чеков**: QR-код ФНС и OCR текста чека (CameraX + Google ML Kit)
- **Мультивалютные счета**: дебетовые карты, наличные, накопительные и кредитные счета с отслеживанием лимита
- **Сплит-транзакции**, регулярные платежи, месячные бюджеты по категориям
- **Приватность**: локальный PII-фильтр вырезает номера карт, CVC, телефоны и ФИО перед отправкой текста во внешний LLM; биометрическая защита входа
- **Офлайн-first**: полная работоспособность без интернета, экспорт истории в CSV/JSON

## Архитектура

Clean Architecture + MVI (`State` / `Intent` / `Effect`), Jetpack Compose + Material 3, Dagger Hilt, Room, CameraX, Google ML Kit, Google GenAI SDK (Gemini). Подробная спецификация — в [CLAUDE.md](CLAUDE.md).

```
app/src/main/java/ru/vogu/smartfinance/
├── core/        # database, DI, network, security, общие UI-компоненты
├── data/        # парсеры (regex/QR/OCR), реализации репозиториев
├── domain/      # модели, интерфейсы репозиториев, use case'ы
└── presentation/# экраны по MVI: chat, scanner, accounts, analytics, budget, settings
```

## Технологический стек

Kotlin 2.x · Jetpack Compose · Navigation Compose · Dagger Hilt · Room 2.6+ · CameraX · ML Kit (Barcode + Text Recognition) · Google GenAI SDK (Gemini 1.5/2.0 Flash) · Kotlinx Serialization

## Быстрый старт

1. Установите Android Studio (Koala+) и JDK 17.
2. Склонируйте репозиторий и откройте его в Android Studio.
3. Скопируйте `local.properties.example` в `local.properties` и укажите:
   ```properties
   sdk.dir=/путь/к/Android/sdk
   GEMINI_API_KEY=ваш_ключ_с_aistudio.google.com
   ```
4. Дайте Gradle синхронизироваться (`./gradlew build` или кнопка Sync в IDE).
5. Запустите конфигурацию `app` на эмуляторе (API 26+) или физическом устройстве.

### Полезные команды

```bash
./gradlew assembleDebug      # собрать debug APK
./gradlew test               # прогнать юнит-тесты (JVM)
./gradlew connectedAndroidTest # инструментальные тесты на устройстве/эмуляторе
```

## Лицензия

[MIT](LICENSE)

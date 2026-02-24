# JAR Config Plugin

Плагин для IntelliJ IDEA, который позволяет отправлять задания с алгоритмами на распределённый вычислительный кластер.

Плагин берёт на себя весь пайплайн: собирает JAR проекта, загружает его в S3, отправляет задание в API **Координатора** и в реальном времени отслеживает выполнение на **SPOT**-нодах.

## Как это работает

```
IntelliJ IDE
    │
    ├─ Панель JAR Config ──► Сборка Gradle/Maven ──► Загрузка в S3 ──► POST /api/v1/jobs
    │                                                                         │
    └─ Панель JAR Monitor ◄────── polling GET /api/v1/jobs/{id} ◄────────────┘
                                  GET /api/v1/spots
```

1. Разработчик настраивает параметры алгоритма (какие алгоритмы, диапазоны итераций/агентов/размерности) в панели **JAR Config**.
2. Нажимает **Build & Submit** — плагин собирает JAR, загружает в S3 и отправляет задание Координатору.
3. Координатор распределяет задачи по SPOT-нодам. Каждая задача запускает JAR с конкретными значениями параметров.
4. **JAR Monitor** опрашивает статус задач каждые 4 секунды. Когда задачи завершены, результаты (значения fopt, лучшие позиции, время выполнения) доступны в панели **Results**.

## Возможности

- **Панель JAR Config** (правый тулбар)
  - Выбор модуля и главного класса
  - Список алгоритмов (через запятую)
  - Перебор параметров: итерации, агенты, размерность — у каждого min / max / step
  - Лог сборки с прогрессом и кнопкой Clear
  - Одна кнопка **Build & Submit**

- **Панель JAR Monitor** (нижний тулбар)
  - Вкладка **Live Monitor** — таблица SPOT-нод (загрузка CPU, запущенные задачи, heartbeat) + очередь задач с цветовой индикацией статусов
  - Вкладка **Results** — загрузка результатов завершённых задач по Job ID
    - Табличное представление: все параметры и метрики
    - **График**: scatter plot (X — любой параметр, Y — fopt, цвет — алгоритм, размер точки — 3-й параметр)
    - Экспорт в CSV или JSON

- **Настройки** (`Settings → Tools → JAR Config Plugin`)
  - URL Координатора
  - S3 endpoint, bucket, key prefix
  - Access/Secret key для S3 (хранятся в настройках IDE, не в файлах проекта)
  - Кнопка **Test Coordinator** — проверяет `/api/v1/health` с таймаутом 5 секунд
  - Кнопка **Test S3 Connection** — проверяет доступ к bucket с таймаутом 12 секунд

## Сценарии использования

### Сценарий 1: Перебор параметров

Есть алгоритм оптимизации на Java/Kotlin, нужно протестировать его на всех комбинациях количества агентов, итераций и размерности задачи.

1. Открой проект алгоритма в IntelliJ.
2. В **JAR Config** укажи алгоритмы (например `sphere,rosenbrock`) и диапазоны каждого параметра.
3. Нажми **Build & Submit**. Плагин создаёт задание, которое разбивается на отдельные задачи по всем комбинациям параметров.
4. В **Live Monitor** видно, как SPOT-ноды забирают задачи и выполняют их параллельно.
5. По завершении перейди в **Results → Load Results** и изучи scatter plot fopt vs. agents, чтобы найти оптимальную конфигурацию.

### Сценарий 2: Сравнение алгоритмов

Укажи алгоритмы `pso,de,ga` с одинаковыми диапазонами параметров. После завершения задания на графике каждый алгоритм отображается своим цветом — сравнение нагляднее некуда.

### Сценарий 3: Итеративная настройка

Сначала запусти широкий перебор. Загрузив результаты, сузь диапазоны до перспективной области и запусти снова. Панель Results принимает любой Job ID — можно сравнивать результаты разных запусков.

### Сценарий 4: Экспорт данных для отчёта

Загрузи результаты, переключись на вкладку Table и нажми **Export CSV** или **Export JSON**. CSV содержит все параметры задачи, fopt, лучшую позицию и время выполнения.

## Требования

- IntelliJ IDEA 2024.1 – 2024.3 (build 241 – 243.x)
- JDK 17+
- Gradle wrapper (`gradlew`) или Maven wrapper (`mvnw`) в собираемом проекте
- Доступный по HTTP Координатор
- S3-совместимое хранилище (Yandex Cloud Object Storage, MinIO, AWS S3 и др.)

## Установка

**Из файла (dev-сборка):**
```bash
.\gradlew.bat build
```
Затем **Settings → Plugins → ⚙ → Install Plugin from Disk** и выбери
`build/distributions/JARConfigPlugin-1.0.0.zip`. Перезапусти IDE.

**Запуск в песочнице (без установки):**
```bash
.\gradlew.bat runIde
```

## Настройка

**Settings → Tools → JAR Config Plugin**

| Параметр | По умолчанию | Описание |
|----------|-------------|----------|
| Coordinator URL | `http://localhost:8081` | Базовый URL REST API Координатора |
| S3 Endpoint | `https://storage.yandexcloud.net` | S3-совместимый endpoint |
| Bucket | `orhestra-algorithms` | Bucket для загрузки JAR-файлов |
| Key Prefix | `experiments` | Префикс ключа объекта, например `experiments` → `experiments/my-algo-1.0.jar` |
| Access Key / Secret Key | — | Хранятся в настройках IDE (не в файлах проекта) |

## Локальное окружение

Инструкции по запуску полного стека локально с Docker (MinIO + Координатор + SPOT-нода): [local-env/README.md](local-env/README.md).

## Поддержка систем сборки

Плагин определяет систему сборки по корню проекта:

| Система сборки | Определение | Команды (в порядке попытки) |
|----------------|-------------|------------------------------|
| Gradle | Наличие `build.gradle.kts` или `build.gradle` | `shadowJar`, `:module:shadowJar`, `jar`, `:module:jar` |
| Maven | Наличие `pom.xml` | `mvn package -DskipTests` |

JAR ищется в `build/libs/` (Gradle) и `target/` (Maven), включая один уровень подпапок.

## Структура проекта

```
plugin root/
├── src/main/kotlin/com/example/jarconfigplugin/
│   ├── actions/        OpenConfigAction
│   ├── model/          JobRequest, JobResult, SpotStatus, TaskResult, ...
│   ├── services/       CoordinatorApiClient, CoordinatorService, S3Service,
│   │                   JarBuildService, JobStateService, BuildToolDetector
│   ├── settings/       PluginSettingsState, PluginSettingsConfigurable, CredentialStore
│   └── ui/             ConfigPanel, MonitorPanel, ResultsPanel, FoptChartPanel,
│                       ConfigToolWindowFactory, MonitorToolWindowFactory
├── src/test/           Юнит-тесты (JUnit 5, MockK, WireMock)
├── ui-tests/           UI-тесты Remote Robot
├── local-env/          Локальное окружение для разработки
│   ├── coordinator/    ← клонируй сюда репо координатора (gitignored)
│   ├── spot-node/      мок SPOT-ноды (Python, запускается в Docker)
│   ├── spot.Dockerfile
│   ├── docker-compose.yml  MinIO + мок SPOT-ноды
│   └── test-algo-project/  пример Maven-проекта алгоритма
└── build.gradle.kts
```

## Стек технологий

| Компонент | Технология |
|-----------|------------|
| Язык | Kotlin 2.1 |
| Сборка | Gradle 8.13, IntelliJ Platform Gradle Plugin 2.11 |
| HTTP-клиент | OkHttp 4.x |
| JSON | Jackson (Kotlin module) |
| S3 | AWS SDK for Java v2 |
| UI | IntelliJ Platform Swing (JB*-компоненты) |
| Тесты | JUnit 5, MockK, WireMock |

## Совместимость

- **sinceBuild**: 241 (IntelliJ IDEA 2024.1)
- **untilBuild**: 243.* (IntelliJ IDEA 2024.3.x)


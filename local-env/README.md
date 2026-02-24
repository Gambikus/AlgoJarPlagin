# Локальное окружение

Локальный стек для сквозного тестирования JAR Config Plugin.

```
Сервисы:
  MinIO        → http://localhost:9000  (S3-совместимое хранилище)
  MinIO UI     → http://localhost:9001  (minioadmin / minioadmin)
  Координатор  → http://localhost:8080  (запускается локально, см. ниже)
  SPOT-нода    → мок на Python, запускается в Docker, подключается к локальному координатору
```

## Требования

- **Docker Desktop** с Compose v2
- **Java 17+** и **Maven 3.9+**
- Git

## Запуск

### 1. Клонировать репо координатора

```bash
cd local-env/

git clone https://github.com/Jexembayev/Orhestra_Soft.git coordinator
```

После клонирования структура папок:

```
local-env/
├── coordinator/      ← склонированный репо координатора (gitignored)
├── spot-node/        ← мок SPOT-ноды (Python)
├── spot.Dockerfile
├── docker-compose.yml
└── test-algo-project/
```

### 2. Запустить MinIO

```bash
docker compose up -d minio minio-init
```

### 3. Запустить координатор локально

Открой отдельный терминал и выполни из папки `coordinator/`:

```bash
cd coordinator
mvn javafx:run
```

Сервер координатора стартует на `http://localhost:8080`.

### 4. Запустить мок SPOT-ноды

```bash
docker compose up -d spot-node
```

Мок SPOT-нода подключится к координатору по адресу `http://host.docker.internal:8080`.

## Настройка плагина

Открой **Settings → Tools → JAR Config Plugin** в IntelliJ и укажи:

| Параметр        | Значение                |
|-----------------|-------------------------|
| Coordinator URL | `http://localhost:8080` |
| S3 Endpoint     | `http://localhost:9000` |
| Bucket          | `orhestra-algorithms`   |
| Key Prefix      | `experiments`           |
| Access Key      | `minioadmin`            |
| Secret Key      | `minioadmin`            |

Нажми **Test Coordinator** и **Test S3 Connection** чтобы проверить подключение перед отправкой задания.

## Тестовое задание

`test-algo-project/` — готовый Maven-проект с алгоритмом. Открой его в IntelliJ как отдельный проект, затем:

1. В панели **JAR Config** выбери модуль `test-algo`.
2. Укажи **Main Class**: `algorithms.Main`.
3. Укажи **Algorithms**: `sphere,rosenbrock`.
4. Нажми **Build & Submit**.

Мок SPOT-нода заберёт задачи, симулирует выполнение и отправит результаты обратно.
В вкладке **Live Monitor** следи за прогрессом, в **Results** — экспортируй результаты.

## Остановка

Остановить всё:

```bash
docker compose down     # остановит MinIO и SPOT-ноду
```

В терминале с координатором нажми **Ctrl+C**.

Остановить и удалить все данные (MinIO bucket, база координатора):

```bash
docker compose down -v
```

Остановить только один сервис, например SPOT-ноду:

```bash
docker compose stop spot-node
```

## Решение проблем

**SPOT-нода не может подключиться к координатору** — убедись, что координатор запущен на порту 8080 до старта `docker compose up -d spot-node`.

**Ошибка S3 "bucket not found"** — контейнер `minio-init` мог не успеть создать bucket. Подожди несколько секунд и повтори, или запусти вручную:
```bash
docker compose run --rm minio-init
```

**Ошибка сборки test-algo в IntelliJ** — проверь, что Maven настроен в IntelliJ (**Settings → Build → Build Tools → Maven**) и выбрана Java 17.

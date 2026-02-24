"""
Mock SPOT node — реализует протокол SPOT-ноды координатора.

Протокол:
  POST /internal/v1/hello             — регистрация, получаем spotId
  POST /internal/v1/heartbeat         — keepalive каждые 5 сек
  POST /internal/v1/tasks/claim       — забираем задачи
  POST /internal/v1/tasks/{id}/complete — репортируем результат
"""

import os
import time
import random
import math
import json
import threading
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

COORDINATOR_URL = os.environ.get("COORDINATOR_URL", "http://coordinator:8081")
HEARTBEAT_INTERVAL = int(os.environ.get("HEARTBEAT_INTERVAL", "5"))
CLAIM_INTERVAL    = int(os.environ.get("CLAIM_INTERVAL",    "3"))
MAX_TASKS         = int(os.environ.get("MAX_TASKS",         "2"))
TOTAL_CORES       = int(os.environ.get("TOTAL_CORES",       "4"))

# --------------------------------------------------------------------------- #

session = requests.Session()
adapter = HTTPAdapter(max_retries=Retry(total=5, backoff_factor=1,
                                        status_forcelist=[500, 502, 503, 504]))
session.mount("http://", adapter)

spot_id: str | None = None
running_tasks = 0
lock = threading.Lock()


def log(msg: str):
    print(f"[spot-node] {msg}", flush=True)


# --------------------------------------------------------------------------- #
# Регистрация

def register() -> str:
    while True:
        try:
            r = session.post(f"{COORDINATOR_URL}/internal/v1/hello",
                             json={"ipAddress": "127.0.0.1"}, timeout=10)
            r.raise_for_status()
            sid = r.json()["spotId"]
            log(f"Registered as {sid}")
            return sid
        except Exception as e:
            log(f"Registration failed: {e} — retry in 5s")
            time.sleep(5)


# --------------------------------------------------------------------------- #
# Heartbeat (фоновый поток)

def heartbeat_loop():
    global running_tasks
    while True:
        time.sleep(HEARTBEAT_INTERVAL)
        try:
            cpu = random.uniform(10, 60)
            with lock:
                rt = running_tasks
            body = {
                "spotId":       spot_id,
                "ipAddress":    "127.0.0.1",
                "cpuLoad":      round(cpu, 1),
                "runningTasks": rt,
                "totalCores":   TOTAL_CORES,
            }
            r = session.post(f"{COORDINATOR_URL}/internal/v1/heartbeat",
                             json=body, timeout=10)
            if not r.ok:
                log(f"Heartbeat failed: {r.status_code} {r.text}")
        except Exception as e:
            log(f"Heartbeat error: {e}")


# --------------------------------------------------------------------------- #
# Симуляция вычисления

def fake_fopt(alg: str, dim: int) -> float:
    if alg in ("sphere",):
        return round(random.uniform(1e-5, 0.05), 8)
    if alg in ("rosenbrock",):
        return round(random.uniform(0.01, 5.0), 8)
    # произвольный: PSO, GA, и т.д.
    return round(random.uniform(-200.0, 0.0), 8)


def fake_best_pos(dim: int) -> list:
    return [round(random.uniform(-0.5, 0.5), 6) for _ in range(dim)]


def execute_task(task: dict):
    """Парсим payload, симулируем выполнение, репортируем результат."""
    global running_tasks
    task_id = task["taskId"]

    try:
        payload = task.get("payload") or "{}"
        if isinstance(payload, str):
            params = json.loads(payload)
        else:
            params = payload
    except Exception:
        params = {}

    alg  = params.get("alg", "unknown")
    dim  = params.get("dimension", 2)
    iters_spec = params.get("iterations", {})
    max_iters  = iters_spec.get("max", 100) if isinstance(iters_spec, dict) else int(iters_spec)

    # Симуляция времени выполнения (0.5..3 сек)
    sleep_time = random.uniform(0.5, 3.0)
    time.sleep(sleep_time)

    fopt     = fake_fopt(alg, dim)
    best_pos = fake_best_pos(dim)
    runtime  = int(sleep_time * 1000)

    result_json = json.dumps({"bestPos": best_pos})
    body = {
        "spotId":     spot_id,
        "runtimeMs":  runtime,
        "iter":       max_iters,
        "fopt":       fopt,
        "resultJson": result_json,
    }

    try:
        r = session.post(
            f"{COORDINATOR_URL}/internal/v1/tasks/{task_id}/complete",
            json=body, timeout=15)
        if r.ok:
            log(f"DONE {task_id}  alg={alg} dim={dim} iter={max_iters}"
                f"  fopt={fopt:.6f}  runtime={runtime}ms")
        else:
            log(f"complete failed for {task_id}: {r.status_code} {r.text}")
    except Exception as e:
        log(f"complete error for {task_id}: {e}")

    with lock:
        running_tasks -= 1


# --------------------------------------------------------------------------- #
# Главный цикл: claim → execute в отдельном потоке

def main_loop():
    global running_tasks
    log(f"Starting claim loop (interval={CLAIM_INTERVAL}s, maxTasks={MAX_TASKS})")
    while True:
        try:
            body = {"spotId": spot_id, "maxTasks": MAX_TASKS}
            r = session.post(f"{COORDINATOR_URL}/internal/v1/tasks/claim",
                             json=body, timeout=10)
            if r.ok:
                tasks = r.json().get("tasks", [])
                if tasks:
                    log(f"Claimed {len(tasks)} task(s)")
                for task in tasks:
                    with lock:
                        running_tasks += 1
                    t = threading.Thread(target=execute_task, args=(task,), daemon=True)
                    t.start()
            else:
                log(f"Claim failed: {r.status_code} {r.text}")
        except Exception as e:
            log(f"Claim error: {e}")

        time.sleep(CLAIM_INTERVAL)


# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    log(f"Connecting to coordinator at {COORDINATOR_URL}")

    # Ждём пока координатор поднимется
    time.sleep(3)

    spot_id = register()

    hb = threading.Thread(target=heartbeat_loop, daemon=True)
    hb.start()

    main_loop()

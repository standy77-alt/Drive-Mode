
# Drive Mode

Неофициальное приложение для управления режимом АКПП (Normal ↔ Sport)
с кнопки на руле автомобилей Haval / GWM с ГУ на Android 9 (SA8155).

## Возможности

- **Удержание кнопки Mute/Back на руле (~1.5 с)** — переключение режима АКПП:
  - NORMAL → SPORT
  - SPORT → NORMAL
- Работает **без root**, **без ADB**, **без READ_LOGS**.
- Автоматический запуск после загрузки ГУ.
- Foreground service (не убивается системой).

## Требования

- ГУ Haval / GWM на Android 9 (SA8155 или аналог).
- Установленный системный сервис `com.gwm.android.adapter.server`
  (штатный, присутствует на всех ГУ GWM/Haval).
- Права `SYSTEM_ALERT_WINDOW` (запрашивается из интерфейса).

## Установка

1. Установите APK.
2. Откройте приложение, дайте разрешение `SYSTEM_ALERT_WINDOW` (кнопка в интерфейсе).
3. Нажмите «Перезапустить службу».
4. Перезагрузите ГУ (опционально) — сервис запустится автоматически.

**Никаких ADB-команд не требуется.**

## Использование

1. Запустите двигатель.
2. **Нажмите и удерживайте Mute (~1.5 с)** на руле.
3. Режим переключится: NORMAL → SPORT.
4. Повторное удержание — SPORT → NORMAL.

## Как это работает

Приложение использует штатный Binder-сервис `GwmAdapterService`
(`com.gwm.android.adapter.server`), который есть в любой прошивке GWM/Haval.

1. **Подписка** на `sys.other.keyevent_notify` (регистрация listener'а
   `IDataChangedListener`).
2. **Push-события** от сервера: `[1006, action]` при нажатии кнопки Mute.
3. **HOLD (action=2)** → переключение режима через `setData`.
4. **Запись** в `car.drive_setting.drive_mode` (0 = Normal, 1 = Sport).

Все операции — **только чтение и подписка**, плюс одна запись в штатное
свойство `drive_mode` (то же, что делает штатное меню автомобиля).

## Безопасность

- Приложение **не использует root**.
- Приложение **не читает logcat**.
- Приложение **не отправляет данные** никуда по сети.
- Единственное изменение состояния автомобиля — переключение режима АКПП
  через штатный API (то же, что доступно в меню ГУ).

## Структура проекта
```
app/src/main/java/com/haval/akppmodes/
├── MainActivity.java              # Главный экран
├── BootReceiver.java              # Автозапуск после загрузки
├── car/
│   ├── CarModeManager.java        # Binder-клиент GA
│   ├── GwmDataListener.java       # Listener для push
│   └── DrivingMode.java           # Enum режимов
└── service/
├── SteeringWheelService.java  # Оркестратор
└── KeyAccessibilityService.java # Запасной путь



## Отладка

```bash
# Смотрим логи работы
logcat -c
logcat | grep -iE "SteeringWheelService|CarModeManager|GwmDataListener|PUSH|HOLD"

# Проверяем, что штатный сервис жив
dumpsys activity services com.gwm.android.adapter.server
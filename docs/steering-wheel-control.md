# Управление приложением кнопками на руле (Bluetooth)

Модуль `feature-wheel` Android-приложения. Позволяет водителю подтверждать/отклонять заказ,
повторять озвучку и выполнять другие действия, не касаясь экрана, — кнопками на руле,
которые в автомобиле отвечают за управление мультимедиа (трек вперёд/назад, play/pause).

Общая архитектура системы — в [architecture.md](architecture.md).

## 1. Как кнопки руля доходят до приложения

```mermaid
sequenceDiagram
    participant W as Кнопка на руле
    participant HU as Головное устройство
    participant BT as Android Bluetooth стек
    participant MSM as MediaSessionManager
    participant App as TaxiPilot MediaSession
    participant Other as Spotify / Навигатор

    W->>HU: нажатие "трек вперёд"
    HU->>BT: AVRCP PASSTHROUGH (FORWARD)
    BT->>MSM: KeyEvent KEYCODE_MEDIA_NEXT
    alt окно решения открыто - сессия TaxiPilot активна и "играет"
        MSM->>App: onMediaButtonEvent
        App-->>HU: метаданные трека = карточка заказа
    else окно закрыто
        MSM->>Other: onMediaButtonEvent
    end
```

Ключевые факты, на которых строится модуль:

1. Кнопки руля подключены к **головному устройству**, а не к телефону. Магнитола транслирует их на
   телефон по профилю **Bluetooth AVRCP** как команды `PASSTHROUGH`. Android превращает их в обычные
   `KeyEvent` с кодами `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_PLAY_PAUSE`
   (иногда раздельно `PLAY` / `PAUSE` / `STOP`, а также `HEADSETHOOK`, `MEDIA_SKIP_FORWARD/BACKWARD`).
2. Android доставляет медиакнопки **одному** приложению — владельцу «последней активной играющей»
   `MediaSession`. Чтобы получать нажатия, нужно в нужный момент стать такой сессией.
3. Кнопки громкости и кнопки «трубка» (принять/отбой вызова, профиль HFP) до приложения
   **не доходят**: громкость обрабатывает магнитола сама, звонки — системный Telecom. Исключение —
   кнопка **«голос»**: по HFP она просит телефон запустить распознавание речи, и Android отдаёт это
   приложению-ассистенту по умолчанию. Это второй канал управления, см. раздел 11.2.
4. На Toyota кнопки Seek шлют AVRCP только при источнике звука «BT Audio» (раздел 11.1); у Prius 20
   штатно нет A2DP/AVRCP вовсе — там остаются голосовая кнопка и BT-пульт.
5. Многие магнитолы показывают на дисплее/приборной панели **метаданные текущего трека**
   (`MediaMetadata`: название, исполнитель, альбом). Это бесплатный HUD: туда можно вывести карточку заказа.

## 2. Режимы работы

| Режим | Когда сессия активна | Что получает Spotify/навигатор | Для кого |
|-------|----------------------|-------------------------------|----------|
| **Окно решения** (по умолчанию) | Только пока открыто окно `Deciding` (пришёл заказ, таймер не истёк). После решения сессия отпускается и музыка возвращается. | Пауза/приглушение на 5–20 с во время заказа | Водители, которые слушают музыку/подкасты |
| **Постоянный** | Всегда, пока идёт смена | Ничего не получает — медиакнопки полностью у TaxiPilot | Водители без музыки; хотят кнопками листать события, повторять рекомендацию и т.п. |
| **Выключен** | Никогда | Всё | Нет кнопок на руле / машина без BT |

Переключение — в настройках и быстрым тумблером в уведомлении foreground-сервиса.

## 3. Раскладка кнопок

Раскладка настраиваемая; значения по умолчанию:

### 3.1 В окне решения (заказ на экране)

| Жест | Действие | Обратная связь |
|------|----------|----------------|
| `NEXT` короткое | **Принять** рекомендацию: подсветить «Принять» на оверлее; если включён `feature-autotap` — нажать «Принять» в Bolt/Uber | TTS «Принято», 1 короткая вибрация, на дисплее магнитолы «ПРИНЯТО» |
| `PREVIOUS` короткое | **Отклонить**: скрыть оверлей, залогировать отказ; если включён автотап — нажать «Отклонить»/закрыть предложение | TTS «Пропускаем», 2 короткие |
| `PLAY_PAUSE` короткое | **Повторить** озвучку заказа (цена, подача, направление, вердикт) | — |
| `PLAY_PAUSE` двойное | Озвучить **детали**: адреса, рейтинг пассажира, способ оплаты, что рядом с точкой высадки (событие/волна) | — |
| `NEXT` длинное (> 600 мс) | Принять **и** сразу построить маршрут к подаче в навигаторе (intent в Waze/Google Maps) | TTS «Принято, веду» |
| `PREVIOUS` длинное | Отклонить и включить **«тихий час»**: не озвучивать заказы ниже порога следующие 10 минут | TTS «Тихий режим 10 минут» |
| `PLAY_PAUSE` длинное | **Стоп автоприёма**: перевести в режим советника до конца смены (работает и вне окна решения) | TTS «Автоприём выключен», 3 короткие |

В режиме автоприёма (см. [order-capture-and-parsing.md](order-capture-and-parsing.md), раздел 4) окно
решения превращается в **окно вето**: карточка озвучена как «Авто: … принимаю», идёт отсчёт 1.5–3 с.
`PREVIOUS` короткое — отменить автоприём этого заказа; `NEXT` короткое — принять немедленно, не дожидаясь
конца отсчёта. Остальные жесты — как в таблице.

### 3.2 Вне окна решения (только в постоянном режиме)

| Жест | Действие |
|------|----------|
| `PLAY_PAUSE` короткое | Озвучить текущую рекомендацию «куда ехать» и ближайшую волну |
| `NEXT` / `PREVIOUS` короткое | Следующее / предыдущее событие в ленте (озвучивается, выводится на дисплей магнитолы) |
| `PLAY_PAUSE` длинное | Старт/стоп перерыва в смене |
| `PLAY_PAUSE` двойное | Озвучить статистику смены: заработано, €/час, заказов принято/отклонено |
| `NEXT` длинное | Построить маршрут к рекомендованной точке |

### 3.3 Почему именно так

- `NEXT` = «да/вперёд», `PREVIOUS` = «нет/назад» — интуитивно и совпадает с расположением кнопок
  на большинстве рулей (вверх/вправо — вперёд).
- Самое частое действие (повтор озвучки) — на самой заметной кнопке `PLAY_PAUSE`.
- Длинные нажатия — для действий с побочными эффектами (запуск навигации, режимы), чтобы
  случайное нажатие не наделало дел.

## 4. Устройство модуля

```mermaid
flowchart TB
    subgraph featureWheel [feature-wheel]
        WS[WheelControlService - часть foreground-сервиса]
        MS[MediaSessionCompat]
        GD[KeyGestureDetector]
        AM[AudioFocus + SilentPlayer]
        MD[MetadataPublisher]
        KM[KeyMap - настройки раскладки]
        WS --> MS
        MS -->|KeyEvent| GD
        GD -->|Gesture| KM
        KM -->|WheelAction| Bus[(ActionBus)]
        WS --> AM
        WS --> MD
    end
    Decision[feature-decision] -->|OrderWindowOpened / Closed| WS
    Bus --> Decision
    Bus --> Events[feature-events]
    Bus --> Shift[feature-shift]
    Bus --> AutoTap[feature-autotap опц.]
```

### 4.1 `WheelControlService`

Не отдельный сервис, а компонент единого foreground-сервиса приложения (тип
`mediaPlayback` добавляется к `location`). Отвечает за захват и освобождение медиасессии:

```kotlin
class WheelController(
    private val context: Context,
    private val audio: AudioManager,
    private val keyMap: KeyMap,
    private val bus: ActionBus,
) {
    private val session = MediaSessionCompat(context, "TaxiPilotWheel").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                return gestures.onKeyEvent(event)
            }
            // Часть магнитол шлёт не KeyEvent, а transport-команды — покрываем оба пути.
            override fun onSkipToNext() { gestures.onSynthetic(KeyEvent.KEYCODE_MEDIA_NEXT) }
            override fun onSkipToPrevious() { gestures.onSynthetic(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            override fun onPlay() { gestures.onSynthetic(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            override fun onPause() { gestures.onSynthetic(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        })
    }
    private val gestures = KeyGestureDetector { gesture -> keyMap.resolve(gesture)?.let(bus::emit) }
    private val silent = SilentPlayer(context)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .build()

    fun grab() {
        audio.requestAudioFocus(focusRequest)
        silent.start()                       // тихий поток делает сессию "играющей" для MediaSessionManager
        session.setPlaybackState(playingState())
        session.isActive = true
    }

    fun release() {
        session.setPlaybackState(stoppedState())
        session.isActive = false
        silent.stop()
        audio.abandonAudioFocusRequest(focusRequest)   // музыка водителя возобновляется
    }

    fun publish(card: WheelCard) = session.setMetadata(
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, card.title)      // "€14.50 · подача 3 мин"
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, card.subtitle)  // "→ Аэропорт T2B · БРАТЬ"
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, card.footer)     // "Next=принять  Prev=нет"
            .build()
    )

    private fun playingState() = PlaybackStateCompat.Builder()
        .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
            or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
        .build()
}
```

Почему нужен тихий поток: `MediaSessionManager` отдаёт медиакнопки той сессии, которая
последней реально воспроизводила звук. Одного `isActive = true` на части прошивок недостаточно,
особенно если Spotify продолжает играть. Тихий файл (`res/raw/silence.ogg`, зациклен, громкость 0)
через ExoPlayer делает сессию «играющей» надёжно. Фокус `TRANSIENT_MAY_DUCK` приглушает, а не
останавливает музыку; TTS-озвучка заказа всё равно идёт поверх.

### 4.2 `KeyGestureDetector`

Превращает поток `KeyEvent` в жесты. Особенности реальных магнитол, которые надо учитывать:

- Приходят пары `ACTION_DOWN`/`ACTION_UP`; некоторые магнитолы шлют только `UP`, некоторые — повторяющиеся
  `DOWN` с `repeatCount > 0` при удержании. Детектор принимает любой из этих вариантов.
- Долгое нажатие: `repeatCount >= 1` с `isLongPress`, либо интервал `DOWN→UP > 600 мс`, либо (если
  приходят только `UP`) — недоступно: длинные жесты тогда отключаются автоматически с пометкой в настройках.
- Двойное нажатие: два коротких в пределах 400 мс. Одиночное срабатывает после истечения окна
  (задержка 400 мс приемлема для «повторить», но **не** для «принять» — поэтому для `NEXT`/`PREVIOUS`
  двойные жесты по умолчанию не назначаются, одиночное срабатывает мгновенно).
- Нормализация кодов: `MEDIA_SKIP_FORWARD → NEXT`, `MEDIA_SKIP_BACKWARD → PREVIOUS`,
  `MEDIA_PLAY | MEDIA_PAUSE | HEADSETHOOK → PLAY_PAUSE`, `MEDIA_STOP → PLAY_PAUSE` (настраиваемо).
- Дребезг: события с одинаковым кодом чаще чем раз в 80 мс игнорируются.

Выход детектора — `Gesture(key: WheelKey, type: SHORT | LONG | DOUBLE)`.

### 4.3 `KeyMap`

Таблица `(контекст, жест) → WheelAction`, где контекст — `DECIDING` или `IDLE`. Хранится в `DataStore`,
редактируется на экране настроек с «режимом обучения»: нажмите кнопку на руле — приложение покажет,
какой код пришёл, и предложит назначить действие. Это решает проблему магнитол с нестандартными кодами.

### 4.4 `MetadataPublisher`

Формирует `WheelCard` для дисплея магнитолы. Ограничения: обычно видно 1–2 строки по 16–40 символов,
обновление может приходить с задержкой до 1–2 с. Поэтому:

- заголовок — самое важное и короткое: `€14.5 · 3 мин · БРАТЬ`;
- подзаголовок — направление и причина: `→ Аэропорт · волна 21:50`;
- после решения — одно слово статуса: `ПРИНЯТО` / `ПРОПУЩЕНО`;
- в постоянном режиме — текущая рекомендация: `BUD 21:40–22:10 · 6 рейсов`.

Некоторые магнитолы кешируют метаданные по `MediaSession`-токену; при смене карточки меняем и
`METADATA_KEY_MEDIA_ID`, чтобы форсировать перерисовку.

### 4.5 `ActionBus`

Простой `SharedFlow<WheelAction>`. Подписчики — `feature-decision` (ACCEPT / DECLINE / REPEAT / DETAILS /
NAVIGATE / QUIET_MODE), `feature-events` (NEXT_EVENT / PREV_EVENT / SPEAK_RECOMMENDATION),
`feature-shift` (TOGGLE_BREAK / SPEAK_STATS), `feature-autotap` (ACCEPT / DECLINE при включённом автотапе).

## 5. Взаимодействие с окном решения

```mermaid
sequenceDiagram
    participant D as feature-decision
    participant W as WheelController
    participant HU as Магнитола
    participant AT as feature-autotap опц.

    D->>W: OrderWindowOpened(offer, verdict, expiresAt)
    W->>W: grab()
    W->>HU: metadata "€14.5 · 3 мин · БРАТЬ"
    HU-->>W: KEYCODE_MEDIA_NEXT
    W->>D: WheelAction.ACCEPT
    D->>D: лог решения, TTS "Принято"
    D->>AT: tapAccept(source=BOLT) [если включено]
    D->>W: OrderWindowClosed
    W->>HU: metadata "ПРИНЯТО"
    W->>W: release() через 1.5 с
```

Детали:

- `grab()` вызывается **до** начала TTS-озвучки, чтобы водитель мог нажать кнопку сразу, не дослушивая.
- Если кнопка нажата в первые 300 мс после открытия окна — это, вероятно, «хвост» предыдущего нажатия
  для музыки; такие нажатия игнорируются (настраиваемо).
- Если заказов пришло два подряд (Bolt и Uber), окно решения относится к **последнему** озвученному;
  `WheelCard` и TTS всегда называют источник: «Bolt: 14 евро…».
- Таймаут окна берётся из уведомления (если там есть таймер), иначе — 15 с по умолчанию.
  По истечении `release()` вызывается автоматически.
- Поскольку `AccessibilityService` нужен приложению в любом случае (им читается карточка), `ACCEPT`
  по кнопке руля по умолчанию **нажимает «Принять» в Bolt/Uber** через `feature-autotap` — это
  «подтверждённый тап», решение принял человек. Отключается в настройках: тогда кнопка лишь
  подсвечивает оверлей и озвучивает «Принято — нажмите в Bolt».
- В режиме автоприёма кнопки руля — главный орган контроля: вето (`PREVIOUS`), немедленное
  подтверждение (`NEXT`) и аварийный стоп автоприёма (длинное `PLAY_PAUSE`).

## 6. Совместимость и граничные случаи

| Ситуация | Поведение |
|----------|-----------|
| Телефон подключён к **Android Auto** | Работает так же: AA использует тот же стек `MediaSession`. Карточка заказа появится в медиа-виджете AA. Оверлей на телефоне при этом не виден — опираемся на TTS + кнопки. |
| Магнитола через **AUX / без BT** | Медиакнопки не доходят. Альтернатива: дешёвый BT-пульт на руль (кольцо/клипса «media remote») — он шлёт те же `KEYCODE_MEDIA_*`, модуль работает без изменений. |
| Гарнитура BT подключена одновременно с магнитолой | Кнопки гарнитуры приходят теми же кодами — работают как дублирующие. |
| Водитель слушает **навигатор + музыку** | Фокус `TRANSIENT_MAY_DUCK` на время окна решения; озвучка навигатора не блокируется (у неё свой transient-фокус, Android смешивает). |
| Магнитола шлёт `PLAY`/`PAUSE` в зависимости от собственного состояния, а не `PLAY_PAUSE` | Оба кода нормализуются в `PLAY_PAUSE`. |
| Магнитола после `release()` не возвращает управление Spotify | Известная особенность части прошивок: после `release()` отправляем системный `KEYCODE_MEDIA_PLAY` через `AudioManager.dispatchMediaKeyEvent`, если до захвата играло другое приложение (запоминаем через `MediaSessionManager.getActiveSessions` — требует разрешения `NotificationListener`, которое у приложения уже есть). |
| Android 14+: foreground-сервис | Декларируем `foregroundServiceType="location|mediaPlayback"` и разрешение `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. |
| Производители с агрессивным энергосбережением | Медиасессия живёт внутри foreground-сервиса; отдельного риска не добавляет. |
| Кнопки руля через **HID** (редко, магнитолы-планшеты на Android) | Приходят как обычные клавиши в активное окно; модуль дополнительно слушает их через оверлей (`View.setOnKeyListener`) — low-priority путь. |

## 7. Разрешения и манифест

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> <!-- только чтобы показать имя магнитолы в настройках -->

<service android:name=".service.TaxiPilotForegroundService"
    android:foregroundServiceType="location|mediaPlayback"
    android:exported="false" />

<receiver android:name="androidx.media.session.MediaButtonReceiver" android:exported="true">
    <intent-filter><action android:name="android.intent.action.MEDIA_BUTTON" /></intent-filter>
</receiver>
```

`MediaButtonReceiver` нужен как резервный путь: если сервис был убит, нажатие кнопки руля
может его поднять (Android доставляет `MEDIA_BUTTON` последнему владельцу сессии).

## 8. Настройки, видимые водителю

- Режим: Выключен / Только при заказе / Постоянно.
- Голосовая кнопка руля: выключена / как клавиша / с распознаванием речи; язык команд.
- Озвучка: телефон / через BT Audio / через Bluetooth-звонок (SCO) — с автоопределением.
- Раскладка кнопок (с «режимом обучения»).
- Задержка игнорирования нажатий после открытия окна (0–500 мс).
- Приглушать музыку при заказе: да / нет (нет = фокус не запрашиваем, только тихий поток).
- Показывать заказ на дисплее магнитолы: да / нет; формат строк.
- Тест: экран, где видно, какие коды приходят с руля в реальном времени.

## 9. Тестирование

- **Без машины**: `adb shell input keyevent KEYCODE_MEDIA_NEXT` / `KEYCODE_MEDIA_PREVIOUS` /
  `KEYCODE_MEDIA_PLAY_PAUSE`; длинное нажатие — `adb shell input keyevent --longpress KEYCODE_MEDIA_NEXT`.
  Проверяем, что события приходят только в окне решения (или всегда — в постоянном режиме),
  и что после `release()` они снова уходят в музыкальное приложение.
- **Юнит-тесты** `KeyGestureDetector`: последовательности DOWN/UP с таймингами → ожидаемые жесты;
  только-UP магнитолы; дребезг; нормализация кодов.
- **Юнит-тесты** `KeyMap`: контексты, пользовательские переопределения, миграция настроек.
- **Инструментальный** тест: сценарий «пришло уведомление → grab → NEXT → ACCEPT → release»
  с фейковым `NotificationListener`.
- **В машине** (чек-лист): три кнопки × короткое/длинное; с играющим Spotify и без; с Android Auto;
  с навигатором; карточка на дисплее магнитолы; возврат музыки после решения; два заказа подряд.
- Матрица совместимости магнитол ведётся в `docs/wheel-compat.md` по мере появления отзывов
  (марка/год, какие коды приходят, показывает ли метаданные, возвращает ли музыку).

## 10. Что модуль не делает

- Не перехватывает кнопки громкости и телефонные кнопки «трубка» руля (технически недоступно без root).
- Не читает CAN-шину автомобиля и не требует OBD-адаптера.
- Не нажимает ничего в Bolt/Uber без явного действия водителя, кроме режима автоприёма, включённого им самим.

## 11. Совместимость с реальным парком: Toyota Prius 20/30/40, Prius+, Corolla Hybrid

Проверено по руководствам Toyota и отчётам владельцев (PriusChat, NCF-гайд Toyota 2010, руководство
Prius 2016, руководство Toyota Touch 2 / Corolla).

| Автомобиль | Штатная магнитола | Bluetooth-профили | Кнопки руля | Что это значит для нас |
|------------|-------------------|-------------------|-------------|------------------------|
| **Prius 20** (XW20, 2004–2009) | Радио/CD; дисплей MFD только в версиях с навигацией | **Только HFP 1.0 + OPP** (звонки). A2DP/AVRCP нет | Громкость, Mode, Seek (только радио/CD), «трубка», «голос» (только с навигацией) | Медиакнопки до телефона **не доходят**. Каналы: (а) кнопка «голос»/«трубка» через HFP — раздел 11.2; (б) BT-пульт на руль; (в) голосовые команды через микрофон телефона |
| **Prius 30** (XW30, 2009–2015) | Базовая без дисплея или Display Audio / Toyota Touch (2012+) | **HFP + A2DP + AVRCP 1.3** с 2010 модельного года (NCF-гайд Toyota). Метаданные трека — на моделях с дисплеем | Громкость, Mode, Seek ▲▼, «трубка», «голос» | Seek ▲▼ = `NEXT`/`PREVIOUS` **только при источнике «BT Audio»** (в режиме радио они переключают станции). Нет дисплея — карточка на магнитоле не выводится, работают TTS + кнопки |
| **Prius 40** (XW50, 2016–2022) | Toyota Touch 2 (7"), Display Audio | HFP + A2DP + AVRCP; метаданные на экране | Громкость, Mode, Seek ▲▼, «трубка», «голос» | Полная поддержка: кнопки + карточка заказа на дисплее. Seek — тоже только в «BT Audio» |
| **Prius+** (ZVW40, 2012–2021) | Toyota Touch / Touch 2 | как Prius 30 (2012+) / Prius 40 | то же | то же |
| **Corolla Hybrid** (E210, 2019+) | Toyota Touch 2 → Toyota Smart Connect (2022+), Android Auto с 2020 | HFP + A2DP + AVRCP | то же | Полная поддержка. При Android Auto медиакнопки идут в активную медиасессию — наш путь работает без изменений |
| Нештатная Android-магнитола (частая замена в такси) | Android HU | BT к телефону как HFP/A2DP, либо телефон вообще не подключён | зависит от установщика | Если телефон подключён по BT — как Prius 30+. Если нет — BT-пульт или голосовой канал |

### 11.1 Ключевое условие для AVRCP-пути: источник «BT Audio»

На всех Toyota кнопки Seek на руле отправляют AVRCP `NEXT`/`PREVIOUS` на телефон только тогда, когда
головное устройство находится в режиме Bluetooth-аудио. Если водитель слушает FM, кнопки переключают
станции и до телефона не доходят. Практические следствия:

- Онбординг объясняет: «переключите магнитолу на Bluetooth-аудио (кнопка Mode) — тогда кнопки руля
  управляют приложением, а озвучка заказов идёт через динамики машины». Озвучка через A2DP — плюс:
  TTS слышно лучше, чем с динамика телефона.
- Если водитель хочет радио — приложение держит «тихий поток» A2DP, но это всё равно требует источника
  «BT Audio» на магнитоле; радио тогда недоступно. Альтернатива для любителей радио — интернет-радио
  на телефоне (тогда источник «BT Audio», а наш модуль работает в режиме «окно решения»).
- На Prius 30 без дисплея в режиме «BT Audio» магнитола показывает только «BT», и это нормально —
  нам хватает кнопок и голоса.

### 11.2 Второй канал: кнопка «голос» через HFP (работает и на Prius 20)

Кнопка «голос» (пиктограмма головы с микрофоном) на руле Prius 30/40/Prius+/Corolla и на Prius 20 с
навигацией отправляет по HFP команду `AT+BVRA=1` — «активировать распознавание речи на телефоне».
Android передаёт её приложению, назначенному **ассистентом по умолчанию** (Настройки → Приложения →
Приложения по умолчанию → Цифровой помощник). На Samsung по умолчанию это Bixby/Google — водитель
один раз переключает на TaxiPilot.

Реализация:

```xml
<activity android:name=".voice.AssistEntryActivity" android:exported="true"
    android:excludeFromRecents="true" android:theme="@style/Transparent">
    <intent-filter>
        <action android:name="android.intent.action.VOICE_COMMAND" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />
        <action android:name="android.intent.action.VOICE_ASSIST" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
<!-- Для роли ассистента на Android 10+ дополнительно: VoiceInteractionService с минимальной реализацией -->
```

Поведение при нажатии «голос»:

| Контекст | Действие |
|----------|----------|
| Открыто окно решения, **короткое нажатие** | Открыть SCO-канал (`BluetoothHeadset.startVoiceRecognition`) — микрофон и динамики машины — и слушать 3 с: «беру / да / принять» → ACCEPT; «нет / пропустить / дальше» → DECLINE; «повтори» → REPEAT. Распознавание on-device (`SpeechRecognizer` с `EXTRA_PREFER_OFFLINE`), словарь из ~10 слов на RU/PL/EN/UK, порог уверенности высокий, при сомнении — переспросить |
| Окно вето автоприёма | То же; молчание = автоприём продолжается |
| Вне окна решения | Озвучить текущую рекомендацию; «статистика», «перерыв», «события» — голосовые команды из раздела 3.2 |
| Настройка «без распознавания» | Кнопка работает как одна физическая клавиша: короткое = ACCEPT/REPEAT по контексту. Для водителей, которым голос неудобен |

Технические детали и ограничения:

- Сразу после получения `AT+BVRA` надо ответить стеку `startVoiceRecognition(device)`, иначе Bluetooth-стек
  ~5 с ждёт и **теряет все следующие нажатия** (подтверждено отчётами по Key Mapper на Galaxy S24, Android 14).
  Завершаем сессию `stopVoiceRecognition` сразу после распознавания — тогда кнопка готова к следующему нажатию.
- Пока открыт SCO, A2DP приостановлен, музыка/TTS на секунды замолкают — приемлемо.
- Назначение TaxiPilot ассистентом по умолчанию отключает вызов Google Ассистента этой кнопкой. Для
  компромисса: длинное нажатие «голос» (или команда «окей гугл») — приложение явно запускает Google-ассистент
  через `Intent(ACTION_VOICE_ASSIST)` с пакетом Google; тестировать на Samsung, поведение зависит от прошивки.
- На некоторых прошивках Samsung требуется отключить Bixby для кнопки, иначе `VOICE_COMMAND` перехватывается им.
- Prius 20 без навигации не имеет кнопки «голос»; кнопка «трубка» без входящего вызова посылает `AT+BLDN`
  (повтор последнего номера) — её перехватить нельзя. Для таких машин — BT-пульт (раздел 11.3).

### 11.3 Третий канал: BT-пульт на руль (для Prius 20 и машин без BT-аудио)

Пульт класса «media button remote» (крепится на спицу руля, ~40–80 zł) подключается к телефону как
отдельное BT-устройство (HID или AVRCP) и шлёт те же `KEYCODE_MEDIA_*`. Модуль работает без изменений,
может быть подключён одновременно с магнитолой (HFP для звонков) — профили не конфликтуют. Озвучка в
этом случае идёт с динамика телефона или через HFP-канал (см. 11.4).

### 11.4 Озвучка в машинах без A2DP

На Prius 20 TTS можно направить в динамики через **HFP SCO** (`AudioManager.startBluetoothSco` +
`STREAM_VOICE_CALL`): качество телефонное, но слышно хорошо и перекрывает шум. Открываем SCO только на
время фразы (2–4 с): магнитола при этом показывает «звонок» и приглушает радио — это даже полезно как
сигнал. Настройка «озвучка: телефон / через Bluetooth-звонок / через BT-аудио» с автоопределением
по профилям подключённого устройства (`BluetoothProfile.A2DP` / `HEADSET`).

### 11.5 Автоопределение и подсказки

При подключении BT-устройства приложение читает его имя и профили и само предлагает режим:

| Обнаружено | Предлагаемый режим |
|------------|--------------------|
| A2DP + AVRCP + HFP | AVRCP-кнопки + голосовая кнопка; озвучка через BT Audio; подсказка переключить источник на «BT Audio» |
| Только HFP | Голосовая кнопка (если есть) + предложение BT-пульта; озвучка через SCO или телефон |
| Отдельный HID/AVRCP-пульт | Пульт как основной ввод |
| Ничего | Экран + озвучка с телефона; подсказка про пульт |

Экран «Тест кнопок» показывает в реальном времени, какие события приходят, — водитель за минуту
понимает, что именно работает в его машине. Результаты (анонимно: модель магнитолы по имени BT,
профили, коды клавиш) отправляются на сервер и пополняют матрицу совместимости.

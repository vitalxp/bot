# Совместимость с автомобилями: управление с руля для парка Uber/Bolt

Дополняет [steering-wheel-control.md](steering-wheel-control.md) (механика модуля `feature-wheel`).
Здесь — какие машины реально ездят в Uber/Bolt в Европе, что умеет их штатная мультимедиа и какой
канал управления приложение выбирает для каждой.

## 1. Какие машины ездят в Uber/Bolt

### 1.1 Польша (основной рынок старта)

Отчёт autoDNA по зарегистрированным такси, 2025:

| Место | Модель | Штук | Доля |
|-------|--------|------|------|
| 1 | Toyota Corolla (в основном Hybrid, E210 2019+) | 16 947 | 14,1 % |
| 2 | Toyota Prius (все поколения, много импорта) | 15 296 | 12,7 % |
| 3 | Skoda Fabia (II/III/IV) | 6 885 | 5,7 % |
| 4 | Toyota Auris (E150/E180, в т.ч. Hybrid) | 5 584 | 4,6 % |
| 5 | Skoda Octavia (II/III/IV) | 3 911 | 3,2 % |

Крупные партнёр-флоты (например, Eres Partner, 400+ машин) закупают Corolla Sedan 1.8 Hybrid сотнями.
Среди корпоративных регистраций 2025 — также Toyota C-HR, Yaris, Yaris Cross, RAV4, Skoda Superb,
Hyundai Tucson, Kia Sportage. Prius+ и Corolla Touring — в сегменте XL/комби.

### 1.2 Другие европейские рынки

| Рынок | Доминирующие модели в ride-hailing | Источник |
|-------|-----------------------------------|----------|
| Великобритания | Toyota Prius / Prius+ (лидер опроса 1 500 водителей 2024/25; 28 % клиентов INSHUR на Toyota, половина — Prius), Hyundai Ioniq, Skoda Octavia / Superb, Toyota Corolla, Ford Galaxy / Tourneo / Mondeo, Citroën Berlingo, Hyundai i30 / i40, VW Passat / Touran, Kia Niro, Nissan Leaf, MG4, Tesla Model 3 | Taxi-Point, INSHUR, Acorn |
| Германия, Австрия, Швейцария | Mercedes E-Class (лидер классического такси в Европе 2018–23), Skoda Superb / Octavia, VW Passat / Touran / Caddy, Toyota Corolla / Camry Hybrid, Tesla Model 3 / Y | Statista |
| Нидерланды, Скандинавия | Tesla Model 3 / Y, Toyota Corolla / Prius, Kia Niro EV, Hyundai Ioniq 5, VW ID.4, Mercedes E / EQE | — |
| Франция, Испания, Италия | Toyota Corolla / Prius / C-HR, Peugeot 308 / 508 / 3008, Renault Mégane / Talisman, Citroën C4 / Berlingo, Skoda Octavia, Dacia Logan / Jogger, Fiat Tipo, Tesla Model 3 | — |
| ЦВЕ и Балканы (Bolt) | Dacia Logan / Sandero / Lodgy / Jogger, Skoda Fabia / Octavia / Rapid, Toyota Prius / Auris / Corolla, Hyundai i30 / Ioniq, Kia Ceed / Niro, Renault Mégane, VW Golf / Jetta, Opel Astra / Insignia, Ford Focus / Mondeo | — |
| Новые электрофлоты (Uber Green / Comfort Electric) | Tesla Model 3 / Y, MG4 / MG5, BYD Dolphin / Seal / Atto 3, Kia Niro EV / EV6, Hyundai Kona / Ioniq 5 / 6, VW ID.3 / ID.4, Nissan Leaf, Renault Zoe / Mégane E-Tech | — |

Требования Uber Poland: UberX — год выпуска 2005+, Comfort — 2018+, Black — 2020+. Значит, в парке
одновременно машины с мультимедиа трёх эпох: без Bluetooth-аудио (2005–2011), с A2DP/AVRCP без экрана
(2010–2016) и с полноценным экраном / Android Auto (2016+). Поддержка нужна всем трём.

## 2. Принцип: поддерживаем не марки, а каналы ввода

Приложение не содержит код «под Toyota» или «под Skoda». Все машины сводятся к четырём стандартным
каналам, которые реализованы один раз:

| Канал | Механизм | Требование к машине |
|-------|----------|---------------------|
| **A. Медиакнопки руля** (`NEXT` / `PREVIOUS` / `PLAY_PAUSE`) | Bluetooth AVRCP → `MediaSession` | Магнитола с A2DP + AVRCP; источник звука — «BT Audio» |
| **B. Кнопка «голос» руля** | Bluetooth HFP `AT+BVRA` → приложение-ассистент по умолчанию → распознавание через микрофон машины (SCO) | Любая магнитола с HFP и кнопкой «голос», которая передаёт её телефону (часто — по **длинному** нажатию) |
| **C. BT-пульт на руль** | Отдельное BT HID / AVRCP устройство → те же `KEYCODE_MEDIA_*` | Ничего; работает даже без штатного Bluetooth |
| **D. Экран + озвучка** | Оверлей, TTS с телефона / через A2DP / через HFP SCO | Ничего |

Поверх каналов — **профиль головного устройства** (`HeadUnitProfile`): небольшой JSON с особенностями
конкретной платформы (какие коды приходят, нужен ли источник «BT Audio», как ведёт себя кнопка «голос»,
показывает ли метаданные, сколько символов). Профили хранятся на сервере, подбираются автоматически по
имени Bluetooth-устройства и набору профилей, уточняются «режимом обучения» и обратной связью водителей.
Новая марка — это новый JSON, не новый релиз.

```json
{
  "id": "toyota-touch2",
  "match": { "bt_name_regex": "^(Toyota|TOYOTA|CAR MULTIMEDIA|Touch)", "profiles": ["A2DP", "AVRCP", "HFP"] },
  "media_keys": { "next": ["MEDIA_NEXT"], "prev": ["MEDIA_PREVIOUS"], "play": ["MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_PLAY_PAUSE"] },
  "requires_bt_audio_source": true,
  "voice_button": { "available": true, "trigger": "short_press", "via": "hfp_bvra" },
  "metadata": { "shown": true, "title_chars": 32, "artist_chars": 32, "refresh_needs_media_id_change": true },
  "long_press_supported": true,
  "tts_route_default": "a2dp",
  "confidence": "verified"
}
```

## 3. Матрица совместимости

Обозначения: **A/B/C/D** — каналы из раздела 2; «источник BT» — кнопки Seek работают только при
источнике «Bluetooth Audio»; уверенность: *подтверждено* (руководство / документация / отчёты
владельцев), *вероятно* (по платформе), *проверить* (нужен тест в машине).

### 3.1 Toyota / Lexus

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные на экране | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|----------------------|----------------|-------------|
| Prius 20 (2004–09) | Радио/CD, MFD только с навигацией | HFP 1.0 + OPP | Нет (только радио/CD) | Только с навигацией; «трубка» без вызова недоступна | Нет | **B** (если есть «голос») / **C**; озвучка через HFP SCO | подтверждено |
| Prius 30 (2010–15), Auris E150 FL (2010–12) | Базовая без экрана / Display Audio / Toyota Touch (2012+) | HFP + A2DP + AVRCP 1.3 | Seek ▲▼ (источник BT), Mode | Есть, HFP | Только на Touch | **A** + **B** | подтверждено |
| Prius+ (2012–21), Auris E180 (2013–19), Yaris III (2011–20) | Toyota Touch / Touch 2 | HFP + A2DP + AVRCP | Seek ▲▼ (источник BT) | Есть, HFP | Да, 2 строки | **A** + **B** | подтверждено |
| Prius 40 (2016–22), C-HR (2016+), Corolla E210 (2019+), Camry XV70 (2019+), RAV4 (2019+), Yaris IV / Yaris Cross | Toyota Touch 2 → Smart Connect (2022+), Android Auto с 2020 | HFP + A2DP + AVRCP; AA | Seek ▲▼ (источник BT или AA) | Есть; на Smart Connect короткое = свой ассистент, длинное = телефон | Да | **A** + **B** | подтверждено / проверить (Smart Connect) |
| Lexus CT / IS / ES / NX (Comfort/Black) | Lexus Remote Touch / Lexus Interface | HFP + A2DP + AVRCP | Да (источник BT) | Есть | Да | **A** + **B** | вероятно |

### 3.2 Volkswagen Group (Skoda, VW, Seat)

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|------------|----------------|-------------|
| Skoda Fabia II (2007–14), Octavia II (2004–13), Rapid (2012–19), VW Golf V/VI, Passat B6/B7, Caddy III | Swing / Blues (без BT), Bolero / Amundsen / Columbus или RCD 310/510, RNS 315/510 с BT-модулем (часто дооснащение) | Чаще **только HFP** (модуль под сиденьем / «Premium» BT с rSAP); A2DP — редко (некоторые Amundsen+, RNS 510 с 9W7) | Только если есть A2DP | Есть на MFL-руле, работает через HFP у части модулей | Нет / только на RNS | **B** (проверить) / **C**; озвучка SCO | подтверждено (HFP-only) |
| Skoda Fabia III (2014–21), Octavia III (2013–20), Superb III (2015–23), Rapid FL, Karoq, Kodiaq, VW Golf VII, Passat B8, Touran II, Caddy IV, Seat Leon III, Toledo IV | MIB1 / MIB2: Swing, Bolero, Amundsen, Columbus; VW Composition Media / Discover | HFP + A2DP + AVRCP (в настройках есть пункт «Bluetooth audio (A2DP/AVRCP)») | Да, Seek ◀▶ на MFL или ролик | Короткое = фирменная голосовая система, **длинное = ассистент телефона** (Google / Siri); по HFP — проверить без Android Auto | Да (Bolero+); Swing — ограниченно | **A** + **B** (длинное нажатие) | подтверждено |
| Skoda Fabia IV (2021+), Octavia IV (2020+), Superb IV, Enyaq, VW Golf VIII, ID.3 / ID.4, Passat B9, Seat/Cupra Leon IV | MIB3, беспроводные Android Auto / CarPlay | HFP + A2DP + AVRCP; AA | Да (сенсорные/ролик) | Короткое = фирменный («Laura»), длинное = телефон | Да | **A** + **B** | вероятно |

### 3.3 Hyundai / Kia

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|------------|----------------|-------------|
| i30 GD (2012–16), i40 (2011–19), Elantra MD, Kia Ceed JD (2012–18), Optima TF | Штатное радио с BT / AVN | HFP + A2DP + AVRCP | Seek ▲▼ (источник BT) | Есть на версиях с навигацией / «звонок» | Да (RDS-строка) | **A** | вероятно |
| Ioniq AE (2016–22), Kona OS, i30 PD (2017+), Tucson TL/NX4, Kia Niro DE/SG2 (2016+), Ceed CD (2018+), Sportage, Stinger, Soul | Display Audio / AVN 5.0 → ccNC; Android Auto | HFP + A2DP + AVRCP; AA | Seek ▲▼ | Длинное нажатие — ассистент телефона | Да | **A** + **B** | подтверждено (владельцы) / проверить BVRA без AA |
| Ioniq 5 / 6, Kona SX2, Kia EV6 / Niro EV SG2 | ccNC, беспроводной AA | HFP + A2DP + AVRCP; AA | Да | Длинное — телефон | Да | **A** + **B** | вероятно |

### 3.4 Ford

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|------------|----------------|-------------|
| Focus II/III (2008–18), Mondeo IV (2007–14), Galaxy / S-Max I, Tourneo Connect I | Ford Audio + Bluetooth / SYNC 1 (2012+) | SYNC 1: HFP + A2DP + AVRCP; «Bluetooth & Voice control» без SYNC: HFP, A2DP с 2010 | Seek ▲▼ (источник BT) | Есть; SYNC — своя голосовая система, телефонный ассистент — проверить | Да (SYNC) | **A** | вероятно |
| Mondeo V (2014–22) и Hybrid, Galaxy / S-Max II, Tourneo Custom / Connect II, Focus IV, Kuga | SYNC 2 / SYNC 3 (2016+) / SYNC 4 | HFP + A2DP + AVRCP; AA (SYNC 3+) | Да | Короткое = SYNC, **длинное = ассистент телефона** (SYNC 3+) | Да | **A** + **B** | вероятно |

### 3.5 Mercedes-Benz / BMW (Comfort, Black)

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|------------|----------------|-------------|
| E-Class W212 (2009–16), C-Class W204, Vito / V-Class W447 | Audio 20 / COMAND NTG 4.x / 5.x | HFP; A2DP + AVRCP с ~2011 (на ранних Audio 20 — опция/«Media Interface») | Да (источник BT Audio) | Есть («LINGUATRONIC»); передача телефону — проверить | Да | **A** | вероятно |
| E-Class W213 (2016+), C W205/W206, EQE, V-Class FL | COMAND NTG 5.5 / MBUX | HFP + A2DP + AVRCP; AA | Сенсорные Touch Control + Seek | Короткое = MBUX («Hey Mercedes»), длинное = телефон | Да | **A** + **B** | вероятно |
| BMW 3/5 (F30/F10, 2010–17) | iDrive CIC / NBT | HFP; A2DP + AVRCP при опции 6NH/6NS (у большинства после 2013 есть) | Да | Есть; длинное — телефон (на ID5+) | Да | **A** | вероятно |
| BMW G20/G30 (2017+), i4, iX | iDrive 6/7/8 | HFP + A2DP + AVRCP; AA | Да | Длинное — телефон | Да | **A** + **B** | вероятно |

### 3.6 Tesla и новые электромобили

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки руля | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|------------------|----------------|------------|----------------|-------------|
| Tesla Model 3 / Y (2017+) | Собственная система, без Android Auto | HFP + A2DP + AVRCP | Левый ролик: влево/вправо = пред./след. трек, вверх/вниз = громкость (источник «Phone»/Bluetooth) | Нажатие ролика = **голос Tesla**, телефону не передаётся | Да (крупно, на экране) | **A** только | подтверждено (владельцы) / проверить коды |
| MG4 / MG5 / ZS EV, BYD Dolphin / Atto 3 / Seal | Android-based инфотейнмент; AA | HFP + A2DP + AVRCP | Да | Обычно свой ассистент; длинное — телефон на части прошивок | Да | **A** (+ **B** проверить) | проверить |
| Nissan Leaf ZE1 (2018+), Qashqai J11/J12 | NissanConnect | HFP + A2DP + AVRCP; AA | Да | Длинное — телефон (с AA); по HFP — проверить | Да | **A** | вероятно |

### 3.7 Renault / Dacia / Stellantis

| Модель (годы) | Мультимедиа | BT-профили | Медиакнопки | Кнопка «голос» | Метаданные | Основной канал | Уверенность |
|---------------|-------------|------------|-------------|----------------|------------|----------------|-------------|
| Dacia Logan II / Sandero II / Lodgy / Dokker (2012–20), Renault Clio IV, Mégane III FL, Captur I | Dacia Plug&Radio / R&Go / Media Nav (Evolution) | HFP + A2DP + AVRCP (Plug&Radio и Media Nav) | **Подрулевой пульт-сателлит** справа за рулём: громкость, источник, ролик треков — работает по AVRCP | Нет (Media Nav Evolution: кнопка на экране) | Media Nav — да; Plug&Radio — 1 строка | **A** (сателлит) | вероятно |
| Dacia Logan III / Sandero III / Jogger (2021+), Renault Clio V, Mégane IV / E-Tech, Captur II, Zoe | Media Control / Media Display / Easy Link / OpenR Link; AA | HFP + A2DP + AVRCP; AA | Сателлит и/или кнопки на руле | Easy Link: кнопка на руле, длинное — телефон (с AA) | Да | **A** + **B** (проверить) | вероятно |
| Peugeot 308 T9 / 508 / 3008 II, Citroën C4 / Berlingo III, Opel Astra K / Insignia B (2016+) | SMEG+ / NAC / Intellilink R4.0 | HFP + A2DP + AVRCP; AA | Сателлит (PSA) или кнопки руля (Opel) | Есть; длинное — телефон (NAC) | Да | **A** | вероятно |
| Fiat Tipo (2016+), 500X | Uconnect 5"/7" | HFP + A2DP + AVRCP | Да (кнопки сзади руля) | Есть; длинное — телефон (Uconnect 7" с AA) | Да | **A** | вероятно |

### 3.8 Нештатные магнитолы

| Тип | Что обычно есть | Канал |
|-----|-----------------|-------|
| Android-магнитола 1/2-DIN (очень распространена в такси на старых Prius / Octavia) | BT к телефону (HFP/A2DP/AVRCP через внешний BT-чип, качество разное); адаптер кнопок руля (CAN/резистивный) → магнитола, оттуда AVRCP на телефон; иногда телефон вовсе не подключают | **A** если телефон подключён как BT-аудио; иначе **C** |
| Pioneer / Kenwood / Sony / JVC 1-DIN с BT | HFP + A2DP + AVRCP; кнопки руля через адаптер | **A** |
| FM-трансмиттер / AUX-BT-приёмник | Только A2DP к приёмнику, кнопки руля не подключены | **C** + озвучка через A2DP |

## 4. Автоопределение профиля

При подключении Bluetooth-устройства (`BluetoothDevice.ACTION_ACL_CONNECTED`, разрешение
`BLUETOOTH_CONNECT`) приложение:

1. Читает имя устройства, класс (`BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE` / `CAR_AUDIO`), поддерживаемые профили
   через `BluetoothAdapter.getProfileProxy(A2DP | HEADSET)` и `getConnectedDevices()`.
2. Ищет `HeadUnitProfile` по `bt_name_regex` (примеры реальных имён: `Toyota Touch`, `CAR MULTIMEDIA`,
   `SKODA_BT_1234`, `VW BT 1234`, `VW Radio`, `MB Bluetooth`, `Tesla`, `SYNC`, `Ford Audio`, `HYUNDAI`,
   `KIA MOTORS`, `Dacia`, `MEDIA-NAV`, `Renault`, `NissanConnect`, `My Car`, `Peugeot`, `Citroën`, `MG`,
   `BYD`, `Pioneer BT`, `CAR-KIT`). Нет совпадения — общий профиль по набору профилей BT.
3. Включает каналы: A2DP+AVRCP → канал A; HFP → предлагает проверить кнопку «голос» (канал B);
   ничего → предлагает BT-пульт.
4. Запускает **экран «Тест кнопок»**: «Нажмите на руле „следующий трек“… „голос“…». Приложение показывает
   пришедшие коды, фиксирует задержки и dedupe, предлагает раскладку. Результат сохраняется локально и
   (анонимно: имя устройства без цифр, профили, коды, модель телефона) отправляется на сервер.
5. При подключении нового устройства, чьё имя неизвестно серверу, после 10 независимых отчётов с одинаковыми
   результатами профиль создаётся автоматически и получает статус «подтверждено сообществом».

## 5. Библиотека особенностей (`quirks`)

| Особенность | Где встречается | Что делает модуль |
|-------------|-----------------|-------------------|
| `requires_bt_audio_source` | Toyota, Hyundai/Kia старые, Mercedes NTG4 | Онбординг-подсказка «переключите источник на Bluetooth»; если кнопки молчат 3 заказа подряд — напоминание |
| `voice_button_long_press` | VW/Skoda MIB, Ford SYNC 3+, Hyundai/Kia DA, MBUX, BMW | Инструкция «удерживайте кнопку», отключение одиночного короткого в раскладке |
| `voice_button_not_forwarded` | Tesla, Dacia Media Nav, часть SYNC 1 | Канал B скрыт в настройках |
| `sends_play_and_pause_separately` | VW MIB, Pioneer | Оба нормализуются в `PLAY_PAUSE` |
| `sends_only_key_up` | Часть магнитол Toyota 2010–2012, Android HU | Длинные нажатия отключаются, раскладка перестраивается на короткие/двойные |
| `repeats_on_hold` | Mercedes NTG, VW MIB | `repeatCount` трактуется как удержание, дребезг 80 мс |
| `metadata_cache_needs_media_id` | Toyota Touch, Hyundai DA | Меняем `METADATA_KEY_MEDIA_ID` при каждом обновлении карточки |
| `metadata_title_chars` (16 / 32 / 64) | Plug&Radio 16, Toyota 32, MIB 64 | Формат карточки заказа подбирается под ширину |
| `no_metadata_display` | Prius 30 без экрана, Fabia II | Карточка не публикуется, только TTS |
| `resumes_music_after_release` = false | Toyota Touch, часть MIB | После `release()` шлём `KEYCODE_MEDIA_PLAY` прежнему плееру |
| `sco_tts_supported` | Все HFP | Для HFP-only машин — озвучка через SCO по умолчанию |
| `android_auto_active` | Любая машина с AA | Оверлей не виден; TTS + кнопки; карточка в медиа-виджете AA |

## 6. Рекомендации водителю по типу машины (онбординг)

| Если у водителя… | Приложение предлагает |
|------------------|-----------------------|
| Corolla / Prius 40 / Auris E180 / любая с экраном и BT-аудио | Источник «Bluetooth», кнопки Seek = принять/отклонить, «голос» = повтор/команды, карточка заказа на экране магнитолы |
| Prius 30 / Auris E150 без экрана | То же без карточки; озвучка через динамики по A2DP |
| Prius 20, Fabia II, Octavia II и прочие HFP-only | Кнопка «голос» (если есть) как «принять/повторить» + озвучка через Bluetooth-звонок (SCO); либо BT-пульт за 40–80 zł — полная раскладка |
| Skoda/VW/Ford/Hyundai/Kia 2014+ | Как Corolla, но «голос» — удерживать |
| Tesla | Левый ролик влево/вправо = отклонить/принять; голос недоступен |
| Dacia / Renault с сателлитом | Ролик сателлита = пред./след. = отклонить/принять; кнопка «источник» не используется |
| Android-магнитола | Подключить телефон по Bluetooth как аудио; если установщик вывел кнопки руля — работает канал A |

## 7. План верификации

1. На старте — ручные тесты в машинах, доступных команде и первым водителям (Prius 30/40, Corolla E210,
   Octavia III, Fabia III, Ioniq): коды кнопок, поведение «голос», метаданные, возврат музыки.
2. Матрица в этом документе переводится в JSON-профили на сервере; статус «проверить» снимается только
   реальным отчётом из «Теста кнопок».
3. Метрика в проде: доля решений, принятых с руля / голосом / экраном, по профилям магнитол — показывает,
   где канал не прижился и нужна доработка.

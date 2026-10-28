package app.taxicom.core.parser

import app.taxicom.core.model.Platform

/** Real order cards from drivers in Tricity (docs/order-capture-and-parsing.md, 1.1). Used by tests and demo mode. */
object SampleCards {
    data class Card(val id: String, val platform: Platform, val lines: List<String>)

    val BOLT_RU_001 = Card(
        "bolt_ru_001", Platform.BOLT,
        listOf(
            "Bolt", "Оплата наличными", "Вне радиуса", "Срок", "16,70 zł (После вычета комиссии)",
            "Отказ не повлияет на процент принятия заказов", "Malwina • 5.0 ★", "45 мин • 45.7 km",
            "Castorama, Grunwaldzka 5, Rumia", "10 мин • 6.1 km", "Nowodworskiego 48A, Gdynia 81-025",
            "Принять", "Отклонить",
        ),
    )

    val UBER_PL_001 = Card(
        "uber_pl_001", Platform.UBER,
        listOf(
            "UberX", "Tylko dla Ciebie", "9,54 zł", "4,95", "Kwota nie zawiera prowizji, ale zawiera podatek…",
            "4 min (1.6 km) do miejsca odbioru", "ulica Marcina Dragana 12, Gdańsk", "7 min (5.3 km) przejazd",
            "ulica płk. Jana Pałubickiego 6, Gdańsk", "Akceptuj",
        ),
    )

    val UBER_RU_001 = Card(
        "uber_ru_001", Platform.UBER,
        listOf(
            "UberX Priority", "Эксклюзивный", "26,46 zł", "Прибл. 3,35 PLN/км", "4,96",
            "С вычетом сервисного сбора, в т.ч. НДС…", "+4,59 zł включено за приоритетную п…",
            "В 2 мин. (1.3 km) от вас", "ulica Romana Wyrobka 1, Гданьск", "Поездка: 18 мин. (6.6 km)",
            "ulica Konna 40, Kolbudy", "Принять",
        ),
    )

    val BOLT_PL_001 = Card(
        "bolt_pl_001", Platform.BOLT,
        listOf(
            "Bolt", "Poza zasięgiem", "8,93 zł (netto z podatkiem)", "Odrzucenie nie wpłynie na Wskaźnik akceptacji",
            "Paweł • 4.9 ★", "16 min • 11.5 km", "Tadeusza Kościuszki 11a, Sopot 81-704", "4 min • 1.9 km",
            "Hotel Radisson Blu Sopot, Bitwy pod Płowcami 54, Sopot", "Akceptuję", "Odrzuć",
        ),
    )

    val ALL = listOf(BOLT_RU_001, UBER_PL_001, UBER_RU_001, BOLT_PL_001)
}

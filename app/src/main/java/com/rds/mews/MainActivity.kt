package com.rds.mews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.ui.theme.MewsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MewsTheme {
                MainScreen()
            }
        }
    }
}

sealed class TabScreen(val title: String, val icon: ImageVector) {
    data object Sources: TabScreen(title = "Источники", Icons.Default.Favorite)
    data object Titles: TabScreen(title = "Заголовки", Icons.Rounded.Menu)
    data object Settings: TabScreen(title = "Настройки", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf<TabScreen>(TabScreen.Sources) }

    val settingsManager = SettingsManager(LocalContext.current.applicationContext)
    val factory = SettingsViewModelFactory(settingsManager)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val newsFeed = listOf(
        Title(
            id = 5,
            time = 1755152804,
            title = "\"Зелёный оазис\": Новый дизайн-проект обещает преобразить центр города",
            text = "Архитектурная компания \"ГринВью\" представила инновационный дизайн-проект \"Зелёный оазис\", который призван превратить заброшенный сквер в самом центре города в современное и экологически чистое общественное пространство. Проект включает в себя установку вертикальных садов, использование энергоэффективного освещения и создание зон для отдыха с доступом к бесплатному Wi-Fi. Ожидается, что реализация проекта начнётся уже в следующем году. Представители городской администрации выразили полную поддержку инициативе, отметив её важность для повышения качества жизни горожан и улучшения экологической обстановки. \"Это не просто парк, а полноценная экосистема, которая будет служить примером для будущих городских проектов\", — заявил главный архитектор города.",
            sources = "\"ГринВью\", Городская администрация, Пресс-служба мэрии, \"Вестник города\"",
            links = "https://greenview.arch/project_oasis\nhttps://mayor.city/press/green_oasis\nhttps://gorod.vestnik/news/zeleniy_oazis"
        ),
        Title(
            id = 6,
            time = 1755162400,
            title = "Нейросеть \"Диалог-5\" прошла тест Тьюринга с отличием",
            text = "Исследовательская лаборатория \"Квантум Майнд\" объявила, что их последняя языковая модель \"Диалог-5\" успешно прошла расширенную версию теста Тьюринга. В ходе слепого теста жюри, состоящее из экспертов в области лингвистики и ИИ, не смогло отличить ответы нейросети от человеческих в 9 из 10 случаев. Это значительный шаг вперед в развитии искусственного интеллекта, способного к осмысленному общению.",
            sources = "\"Квантум Майнд\", AI Today, Журнал \"Нейросети и будущее\"",
            links = "https://quantum-mind.ai/dialog5\nhttps://aitoday.com/news/dialog5-turing\nhttps://neuro-journal.net/latest"
        ),
        Title(
            id = 7,
            time = 1755173200,
            title = "Миссия \"Европа-Океан\" подтвердила наличие жидкой воды под ледяной корой",
            text = "NASA опубликовало первые данные, полученные с посадочного модуля миссии \"Европа-Океан\". Анализ проб, взятых с глубины 10 метров под поверхностью ледяной коры спутника Юпитера, показал наличие сложного химического состава и жидкой соленой воды. Это открытие значительно повышает шансы на обнаружение внеземной микробной жизни.",
            sources = "NASA, JPL, Журнал Nature, Space.com",
            links = "https://nasa.gov/europa-ocean/news\nhttps://www.nature.com/articles/europa_water_confirmed\nhttps://space.com/europa-ocean-mission-update"
        ),
        Title(
            id = 8,
            time = 1755141000,
            title = "Центробанк анонсировал запуск цифрового рубля в пилотном режиме",
            text = "Глава Центробанка объявила о старте пилотного проекта по использованию цифрового рубля для розничных операций в трёх регионах страны. На первом этапе в проекте примут участие пять крупнейших банков. Пользователи смогут открывать цифровые кошельки и совершать переводы и платежи без комиссий. Полномасштабный запуск запланирован на конец следующего года.",
            sources = "Центральный банк РФ, Ведомости, РБК Финансы",
            links = "https://cbr.ru/press/digital_ruble_pilot\nhttps://vedomosti.ru/finance/digital-ruble\nhttps://rbc.ru/finances/cbr_pilot"
        ),
        Title(
            id = 9,
            time = 1755180500,
            title = "Сборная по хоккею выиграла чемпионат мира в драматичном финале",
            text = "В напряженнейшем финальном матче чемпионата мира по хоккею национальная сборная одержала волевую победу над командой Канады со счетом 4:3. Победную шайбу забросил капитан команды за 30 секунд до конца овертайма, завершив блестящую комбинацию. Эта победа стала первой для сборной за последние пять лет.",
            sources = "IIHF, Спорт-Экспресс, Матч ТВ, Чемпионат.com",
            links = "https://iihf.com/en/events/2025/wm/news/final_game\nhttps://sport-express.ru/hockey/world/reviews/final-match\nhttps://championat.com/hockey/article-12345"
        ),
        Title(
            id = 10,
            time = 1755132000,
            title = "Открылась выставка \"Забытые авангардисты\" в Третьяковской галерее",
            text = "Новая Третьяковка представила уникальную экспозицию работ художников русского авангарда, чьи имена были незаслуженно забыты. Более 200 полотен из частных коллекций и государственных архивов, созданных в период с 1910 по 1930-е годы, впервые показаны широкой публике. Выставка проливает свет на малоизученные течения в искусстве того времени.",
            sources = "Третьяковская галерея, Афиша Daily, Культура.РФ",
            links = "https://www.tretyakovgallery.ru/exhibitions/zabytye-avangardisty/\nhttps://daily.afisha.ru/exhibitions/review-forgotten-avant-garde\nhttps://culture.ru/news/258395"
        ),
        Title(
            id = 11,
            time = 1755191000,
            title = "Создан аккумулятор на основе графена, заряжающийся за 60 секунд",
            text = "Стартап \"GigaCharge\" продемонстрировал прототип аккумулятора для смартфона, способного полностью зарядиться всего за одну минуту. Технология основана на использовании графеновых суперконденсаторов. Компания обещает, что новые батареи будут не только быстрыми, но и значительно долговечнее литий-ионных аналогов. Начало массового производства ожидается через два года.",
            sources = "GigaCharge Inc., TechCrunch, The Verge",
            links = "https://gigacharge.tech/press-release\nhttps://techcrunch.com/2025/08/12/gigacharge-60-second-battery/\nhttps://www.theverge.com/graphene-battery-future"
        ),
        Title(
            id = 12,
            time = 1755202000,
            title = "Генетики научились редактировать несколько генов одновременно с высокой точностью",
            text = "Ученые из Института геномных исследований разработали новую методику на базе CRISPR, названную \"Multi-Edit\". Она позволяет вносить изменения в десятки генов одновременно с точностью до 99.8%. Это открывает новые горизонты в лечении сложных генетических заболеваний, таких как рак и аутоиммунные расстройства.",
            sources = "Институт геномных исследований, Science, BioMed Central",
            links = "https://genomic-research.edu/publications/multi-edit\nhttps://www.science.org/doi/10.1126/science.12345\nhttps://biomedcentral.com/news/multi-edit-crispr"
        ),
        Title(
            id = 13,
            time = 1755213000,
            title = "Завершено строительство самого длинного подводного тоннеля в Европе",
            text = "Тоннель под проливом, соединяющий Данию и Германию, официально открыт для движения. Протяженность тоннеля составляет 18 километров. Проект, реализованный за 8 лет, сократит время в пути между странами с часа до 10 минут на поезде. Тоннель включает в себя как железнодорожные пути, так и автомагистраль.",
            sources = "Femern A/S, Reuters, Deutsche Welle",
            links = "https://femern.com/en/press/tunnel-opening\nhttps://www.reuters.com/world/europe/denmark-germany-tunnel-opens\nhttps://dw.com/news/femern-tunnel"
        ),
        Title(
            id = 14,
            time = 1755129000,
            title = "Лесные пожары в Сибири достигли рекордных масштабов",
            text = "По данным спутникового мониторинга, площадь лесных пожаров в Сибири и на Дальнем Востоке превысила 5 миллионов гектаров. Экологи бьют тревогу, заявляя о катастрофических последствиях для экосистемы региона и климата планеты. В тушении задействованы силы МЧС и армейские подразделения, однако ситуация остается критической.",
            sources = "Гринпис России, МЧС РФ, Авиалесоохрана, NASA Earth Observatory",
            links = "https://greenpeace.ru/act/siberia-fires\nhttps://mchs.gov.ru/operational-info/siberia\nhttps://earthobservatory.nasa.gov/images/150000/siberian-fires"
        ),
        Title(
            id = 15,
            time = 1755224000,
            title = "Крупнейший онлайн-ритейлер приобретает сеть кинотеатров \"Киномир\"",
            text = "Технологический гигант \"ShopSphere\" объявил о сделке по покупке второй по величине в стране сети кинотеатров \"Киномир\". Сумма сделки оценивается в 2.5 миллиарда долларов. Аналитики полагают, что \"ShopSphere\" планирует интегрировать кинотеатры в свою экосистему подписки, предлагая эксклюзивные показы и мероприятия для своих клиентов.",
            sources = "ShopSphere Press, The Wall Street Journal, Bloomberg",
            links = "https://shopsphere.com/press/kinomir-acquisition\nhttps://wsj.com/articles/shopsphere-buys-kinomir\nhttps://bloomberg.com/news/tech-giant-enters-cinema-business"
        ),
        Title(
            id = 16,
            time = 1755235000,
            title = "Режиссер Акира Сайто получил \"Золотую пальмовую ветвь\" в Каннах",
            text = "Главный приз 78-го Каннского кинофестиваля достался японскому режиссеру Акире Сайто за его философскую драму \"Шепот песка\". Фильм рассказывает историю бывшего архитектора, который находит новый смысл жизни в уединении на побережье. Критики назвали картину \"визуально безупречной и глубоко трогательной\".",
            sources = "Festival de Cannes, Variety, The Hollywood Reporter",
            links = "https://www.festival-cannes.com/en/palmares/2025\nhttps://variety.com/2025/film/reviews/whisper-of-sand-review\nhttps://hollywoodreporter.com/news/cannes-palme-dor-2025"
        ),
        Title(
            id = 17,
            time = 1755246000,
            title = "Археологи обнаружили затерянный город цивилизации Майя в джунглях",
            text = "Международная экспедиция с помощью технологии лидарного сканирования обнаружила руины крупного города цивилизации Майя на полуострове Юкатан. Город, названный \"Ок-Кан\" (Душа Змеи), включает в себя пирамиды, дворцы и стадион для игры в мяч. Находка может перевернуть представления о политической организации древних Майя.",
            sources = "National Geographic, Институт антропологии Мехико, Reuters",
            links = "https://www.nationalgeographic.com/history/article/lost-city-ok-kan-found\nhttps://inah.gob.mx/press/ok-kan-discovery\nhttps://reuters.com/science/maya-city-lidar"
        ),
        Title(
            id = 18,
            time = 1755257000,
            title = "Всемирная организация здравоохранения рекомендует сократить потребление ультра-обработанных продуктов",
            text = "ВОЗ выпустила новое руководство, в котором настоятельно рекомендуется ограничить потребление ультра-обработанных пищевых продуктов, таких как газированные напитки, чипсы и фастфуд. Исследования связывают их чрезмерное употребление с повышенным риском ожирения, диабета 2 типа и сердечно-сосудистых заболеваний.",
            sources = "Всемирная организация здравоохранения (ВОЗ), The Lancet, BBC Health",
            links = "https://www.who.int/news/item/2025-08-12-ultra-processed-foods-guidelines\nhttps://www.thelancet.com/journals/lancet/article/new-who-guidelines\nhttps://www.bbc.com/news/health-654321"
        ),
        Title(
            id = 19,
            time = 1755268000,
            title = "Автоконцерн \"Восход\" представил первый серийный летающий автомобиль",
            text = "На международном автосалоне в Женеве компания \"Восход\" представила модель \"Стрела-1\" — первый в мире серийный летающий автомобиль. Аппарат способен передвигаться как по дорогам общего пользования, так и совершать вертикальный взлет и посадку. Предзаказы начнут принимать в конце года, цена стартует от 300 тысяч евро.",
            sources = "Автоконцерн \"Восход\", Top Gear, Autocar",
            links = "https://voshod-auto.com/strela1\nhttps://www.topgear.com/car-news/voshod-strela-1-reveal\nhttps://www.autocar.co.uk/car-news/geneva-motor-show/flying-car"
        ),
        Title(
            id = 20,
            time = 1755279000,
            title = "Новый альбом группы \"Эхо\" возглавил мировые чарты за сутки",
            text = "Долгожданный пятый студийный альбом инди-рок группы \"Эхо\" под названием \"Цифровые сны\" занял первые строчки в стриминговых сервисах более чем в 50 странах мира всего за 24 часа после релиза. Критики отмечают смелое смешение жанров и глубокие тексты, посвященные жизни в цифровую эпоху.",
            sources = "Billboard, Rolling Stone, Pitchfork",
            links = "https://www.billboard.com/music/rock/echo-digital-dreams-charts\nhttps://www.rollingstone.com/music/music-album-reviews/echo-digital-dreams/\nhttps://pitchfork.com/reviews/albums/echo-digital-dreams/"
        ),
        Title(
            id = 21,
            time = 1755285000,
            title = "В городском зоопарке родился редкий белый тигренок",
            text = "Большое событие в столичном зоопарке: у пары амурских тигров родился детеныш с редкой мутацией, приведшей к белому окрасу. Малыш, которого назвали Снежок, чувствует себя хорошо и находится под постоянным наблюдением ветеринаров. Это первый случай рождения белого тигренка в неволе в этом десятилетии.",
            sources = "Московский зоопарк, ТАСС, Интерфакс",
            links = "https://moscowzoo.ru/about-zoo/news/belyy-tigrenok/\nhttps://tass.ru/obschestvo/1234567\nhttps://www.interfax.ru/moscow/765432"
        ),
        Title(
            id = 22,
            time = 1755291000,
            title = "На фондовом рынке зафиксирована крупнейшая за год коррекция",
            text = "Ведущие мировые индексы упали в среднем на 5-7% на фоне опасений по поводу инфляции и ужесточения монетарной политики центральными банками. Наибольшие потери понесли акции технологических компаний. Аналитики называют происходящее \"здоровой коррекцией\", но предупреждают о возможной дальнейшей волатильности.",
            sources = "РБК Инвестиции, Financial Times, MarketWatch",
            links = "https://quote.rbc.ru/news/article/6308a0d99a79476d0f1a4e52\nhttps://www.ft.com/content/market-correction-fears\nhttps://www.marketwatch.com/story/tech-stocks-plunge"
        ),
        Title(
            id = 23,
            time = 1755298000,
            title = "Власти Парижа объявили о планах сделать набережные Сены полностью пешеходными",
            text = "Мэрия Парижа представила амбициозный план по полному закрытию автомобильного движения на верхних набережных Сены в историческом центре города к 2028 году. Проект предполагает создание обширных зеленых зон, велодорожек и пространств для отдыха. Цель инициативы — снизить уровень шума и загрязнения воздуха.",
            sources = "Mairie de Paris, Le Monde, The Guardian Cities",
            links = "https://www.paris.fr/berges-de-seine\nhttps://www.lemonde.fr/planete/article/paris-berges-pietonnes.html\nhttps://www.theguardian.com/world/cities/paris-seine-riverbanks"
        ),
        Title(
            id = 24,
            time = 1755305000,
            title = "Обнаружена уязвимость в популярном протоколе шифрования WPA4",
            text = "Команда исследователей кибербезопасности из \"CipherLab\" выявила критическую уязвимость в новом стандарте Wi-Fi шифрования WPA4. Уязвимость, названная \"KeyLeak\", потенциально позволяет злоумышленнику в той же сети перехватывать и расшифровывать трафик. Производителям оборудования рекомендовано срочно выпустить патчи.",
            sources = "CipherLab Security, ZDNet, Ars Technica",
            links = "https://cipherlab.sec/research/keyleak\nhttps://www.zdnet.com/article/wpa4-vulnerability-keyleak/\nhttps://arstechnica.com/security/2025/08/wpa4-flaw-leaves-wifi-vulnerable/"
        )
    )

    Scaffold(
        bottomBar = {
            MyBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    selectedTab = newTab
                },
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            TabScreen.Sources -> SourcesGrid(
                listOf("Элемент 1", "Элемент 2", "Элемент 3", "Элемент 4", "Элемент 5", "Элемент 6", "Элемент 7", "Элемент 8", "Элемент 9", "Элемент 10", "Элемент 11", "Элемент 12",
                    "Элемент 13", "Элемент 14", "Элемент 15", "Элемент 16", "Элемент 17", "Элемент 18","Элемент 19", "Элемент 20", "Элемент 21", "Элемент 22", "Элемент 23", "Элемент 24","Элемент 25", "Элемент 26", "Элемент 27", "Элемент 28", "Элемент 29", "Элемент 30","Элемент 31", "Элемент 32", "Элемент 33", "Элемент 34", "Элемент 35", "Элемент 36","Элемент 37", "Элемент 38", "Элемент 39", "Элемент 40", "Элемент 41", "Элемент 42",),
                modifier = Modifier.padding(paddingValues)
            )
            TabScreen.Titles -> TitlesGrid(
                newsFeed,
                modifier = Modifier.padding(paddingValues)
            )
            else -> SettingsGrid(
                modifier = Modifier.padding(paddingValues),
                settingsModel = settingsViewModel
            )
        }
    }
}

@Composable
fun MyBottomBar(selectedTab: TabScreen, onTabSelected: (TabScreen) -> Unit) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                },
                label = {
                    Text(text = tab.title)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.background,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}
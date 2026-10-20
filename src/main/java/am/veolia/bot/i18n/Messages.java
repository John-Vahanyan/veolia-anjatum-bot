package am.veolia.bot.i18n;

import am.veolia.bot.model.Language;

import java.util.EnumMap;
import java.util.Map;

/**
 * All bot-authored UI text (greetings, confirmations, errors, help) in all
 * supported languages. The outage announcement text itself is never
 * translated here — it's forwarded to users verbatim in its original Armenian.
 */
public final class Messages {

    private Messages() {
    }

    public static final String CHOOSE_LANGUAGE_HY = "Խնդրում ենք ընտրել լեզուն.";
    public static final String CHOOSE_LANGUAGE_EN = "Please choose a language.";
    public static final String CHOOSE_LANGUAGE_RU = "Пожалуйста, выберите язык.";

    public static String chooseLanguagePrompt() {
        return CHOOSE_LANGUAGE_HY + "\n" + CHOOSE_LANGUAGE_EN + "\n" + CHOOSE_LANGUAGE_RU;
    }

    public static String welcome(Language lang) {
        return text(lang,
                """
                Բարև ձեզ։ Ես ծանուցումներ եմ ուղարկում «Վեոլիա Ջուր» ընկերության ջրանջատումների \
                մասին։

                ℹ️ Սա «Վեոլիա Ջուր»-ի պաշտոնական բոտը չէ և կապ չունի ընկերության հետ։ Բոտը պարզապես հետևում է «Վեոլիա Ջուր»-ի հանրային Telegram ալիքին և Ձեզ է ուղարկում միայն Ձեր հասցեին վերաբերող հայտարարությունները։ Ջրի, վթարների կամ հաշիվների հարցերով խնդրում ենք դիմել անմիջապես «Վեոլիա Ջուր»-ին։

                Բաժանորդագրվեք Ձեզ հետաքրքրող փողոցի կամ թաղամասի անվանը, և ես կտեղեկացնեմ Ձեզ, \
                երբ նոր հայտարարություն հայտնվի այդ հասցեի վերաբերյալ։ Կարող եք գրել լատինատառ \
                կամ ռուսերեն տառերով՝ ես կփորձեմ վերածել հայերենի։

                Օգտագործեք ներքևի կոճակները, կամ այս հրամանները.
                /subscribe <բառ> — բաժանորդագրվել
                /unsubscribe <բառ> — չեղարկել բաժանորդագրությունը
                /list — ցուցադրել բաժանորդագրությունները
                /language — փոխել լեզուն
                /support — առաջարկ կամ սխալի մասին հաղորդում
                /help — օգնություն""",
                """
                Hi! I send notifications about water outage announcements from Veolia Jur.

                ℹ️ This is not an official Veolia Jur bot and is not affiliated with the company. The bot simply follows Veolia Jur's public Telegram channel and forwards you only the announcements that concern your address. For questions about water supply, accidents or bills, please contact Veolia Jur directly.

                Subscribe to a street or district name you care about, and I'll notify you \
                whenever a new announcement mentions it. You can type it in English or Russian \
                letters — I'll try to convert it to Armenian automatically.

                Use the buttons below, or these commands:
                /subscribe <keyword> — add a subscription
                /unsubscribe <keyword> — remove a subscription
                /list — show your subscriptions
                /language — change language
                /support — send a suggestion or report a bug
                /help — show help""",
                """
                Привет! Я отправляю уведомления об отключениях воды от «Veolia Jur».

                ℹ️ Это не официальный бот «Veolia Jur», и он никак не связан с компанией. Бот просто следит за публичным Telegram-каналом «Veolia Jur» и пересылает вам только те объявления, которые касаются вашего адреса. По вопросам водоснабжения, аварий или счетов обращайтесь напрямую в «Veolia Jur».

                Подпишитесь на интересующую вас улицу или район, и я сообщу вам, как только \
                появится новое объявление об этом адресе. Можно писать латиницей или \
                по-русски — я постараюсь автоматически преобразовать это в армянский.

                Используйте кнопки внизу или эти команды:
                /subscribe <слово> — добавить подписку
                /unsubscribe <слово> — удалить подписку
                /list — показать ваши подписки
                /language — изменить язык
                /support — предложение или сообщение об ошибке
                /help — показать справку"""
        );
    }

    public static String help(Language lang) {
        return text(lang,
                """
                Կարող եք օգտագործել ներքևի կոճակները, կամ հետևյալ հրամանները.
                /subscribe <բառ> — բաժանորդագրվել փողոցի կամ թաղամասի անվանը
                /unsubscribe <բառ> — չեղարկել բաժանորդագրությունը
                /list — ցուցադրել ընթացիկ բաժանորդագրությունները
                /language — փոխել լեզուն
                /menu — ցուցադրել կոճակների ընտրացանկը
                /support — առաջարկ ուղարկել կամ սխալի մասին հաղորդել
                /help — այս հաղորդագրությունը""",
                """
                You can use the buttons below, or these commands:
                /subscribe <keyword> — subscribe to a street or district name
                /unsubscribe <keyword> — remove a subscription
                /list — show your current subscriptions
                /language — change language
                /menu — show the button menu
                /support — send a suggestion or report a bug
                /help — show this message""",
                """
                Можно использовать кнопки внизу или эти команды:
                /subscribe <слово> — подписаться на улицу или район
                /unsubscribe <слово> — удалить подписку
                /list — показать текущие подписки
                /language — изменить язык
                /menu — показать меню кнопок
                /support — отправить предложение или сообщить об ошибке
                /help — показать это сообщение"""
        );
    }

    public static String languageUpdated(Language lang) {
        return text(lang,
                "Լեզուն փոփոխվել է հայերենի։",
                "Language set to English.",
                "Язык изменён на русский.");
    }

    public static String subscribeUsage(Language lang) {
        return text(lang,
                "Օգտագործում. /subscribe <բառ>",
                "Usage: /subscribe <keyword>",
                "Использование: /subscribe <слово>");
    }

    public static String subscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք՝ «" + keyword + "»։",
                "Subscribed to \"" + keyword + "\".",
                "Вы подписались на «" + keyword + "».");
    }

    /**
     * Follow-up tip when the typed street name contains a number (e.g. a building number).
     * {@code suggestion} is the name with the numbers stripped, or blank if nothing was left.
     */
    public static String streetNumberTip(Language lang, String suggestion) {
        boolean hasSuggestion = suggestion != null && !suggestion.isBlank();
        return text(lang,
                "💡 Խորհուրդ՝ հայտարարություններում փողոցը սովորաբար նշվում է շենքերի համարների հետ "
                        + "միասին, ուստի համարով բաժանորդագրությունը կարող է բաց թողնել դրանք։ Ավելի լավ "
                        + "արդյունքի համար բաժանորդագրվեք միայն փողոցի անվանը"
                        + (hasSuggestion ? "՝ օրինակ «" + suggestion + "»։" : "՝ առանց համարի։")
                        + "\n\nԱյս բաժանորդագրությունը կարող եք հեռացնել «" + MENU_UNSUBSCRIBE_HY + "» կոճակով։",
                "💡 Tip: announcements usually list a street together with several building numbers, "
                        + "so a subscription that includes a number may miss them. For better results, "
                        + "subscribe to just the street name"
                        + (hasSuggestion ? ", e.g. \"" + suggestion + "\"." : ", without the number.")
                        + "\n\nYou can remove this subscription with the \"" + MENU_UNSUBSCRIBE_EN + "\" button.",
                "💡 Совет: в объявлениях улица обычно указывается вместе с номерами домов, поэтому "
                        + "подписка с номером может их пропустить. Для лучшего результата подпишитесь только "
                        + "на название улицы"
                        + (hasSuggestion ? ", например «" + suggestion + "»." : ", без номера.")
                        + "\n\nЭту подписку можно удалить кнопкой «" + MENU_UNSUBSCRIBE_RU + "».");
    }

    /** Used when the stored keyword differs from what the user typed (transliterated to Armenian). */
    public static String subscribedTransliterated(Language lang, String original, String armenian) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք՝ «" + armenian + "» (փոխադրվեց «" + original + "»-ից)։\n\n"
                        + "Նկատի ունեցեք. «Վեոլիա Ջուր»-ի հայտարարությունները հրապարակվում են միայն "
                        + "հայերենով, ուստի Ձեր բառը ավտոմատ կերպով փոխադրվեց հայերենի։ Համընկնումը "
                        + "ստուգելիս թույլատրվում է մինչև 2 տառի տարբերություն։",
                "Subscribed to \"" + armenian + "\" (converted from \"" + original + "\").\n\n"
                        + "Please note: Veolia Jur only shares announcements in Armenian, so we "
                        + "automatically converted your word to Armenian. We'll allow up to 2 letter "
                        + "differences when matching it against future posts.",
                "Вы подписались на «" + armenian + "» (преобразовано из «" + original + "»).\n\n"
                        + "Обратите внимание: «Veolia Jur» публикует объявления только на армянском "
                        + "языке, поэтому ваше слово было автоматически преобразовано в армянский. При "
                        + "поиске совпадений допускается расхождение до 2 букв.");
    }

    public static String alreadySubscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք արդեն բաժանորդագրված եք՝ «" + keyword + "»։",
                "You're already subscribed to \"" + keyword + "\".",
                "Вы уже подписаны на «" + keyword + "».");
    }

    public static String unsubscribeUsage(Language lang) {
        return text(lang,
                "Օգտագործում. /unsubscribe <բառ>",
                "Usage: /unsubscribe <keyword>",
                "Использование: /unsubscribe <слово>");
    }

    public static String unsubscribed(Language lang, String keyword) {
        return text(lang,
                "Բաժանորդագրությունը՝ «" + keyword + "», չեղարկվեց։",
                "Unsubscribed from \"" + keyword + "\".",
                "Подписка на «" + keyword + "» отменена.");
    }

    public static String notSubscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք բաժանորդագրված չեք «" + keyword + "»-ին։",
                "You're not subscribed to \"" + keyword + "\".",
                "Вы не подписаны на «" + keyword + "».");
    }

    public static String listEmpty(Language lang) {
        return text(lang,
                "Դուք դեռ ոչ մի բաժանորդագրություն չունեք։ Օգտագործեք /subscribe <բառ>։",
                "You have no subscriptions yet. Use /subscribe <keyword>.",
                "У вас пока нет подписок. Используйте /subscribe <слово>.");
    }

    public static String listHeader(Language lang) {
        return text(lang, "Ձեր բաժանորդագրությունները.", "Your subscriptions:", "Ваши подписки:");
    }

    public static String unknownCommand(Language lang) {
        return text(lang,
                "Անհայտ հրաման։ Օգտագործեք /help՝ հրամանների ցանկը տեսնելու համար։",
                "Unknown command. Use /help to see available commands.",
                "Неизвестная команда. Используйте /help, чтобы увидеть список команд.");
    }

    public static String newOutageHeader(Language lang) {
        return text(lang,
                "🚨 Նոր ջրանջատում՝ ըստ Ձեր բաժանորդագրության.",
                "🚨 New outage matching your subscription:",
                "🚨 Новое отключение воды по вашей подписке:");
    }

    // Persistent reply-keyboard menu button labels. Kept as public constants (not
    // just accessor methods) because incoming button-press messages need to be
    // matched against these exact strings regardless of the user's current language.
    public static final String MENU_SUBSCRIBE_HY = "Բաժանորդագրվել ➕";
    public static final String MENU_SUBSCRIBE_EN = "Subscribe ➕";
    public static final String MENU_SUBSCRIBE_RU = "Подписаться ➕";
    public static final String MENU_UNSUBSCRIBE_HY = "Չեղարկել բաժանորդագրությունը ➖";
    public static final String MENU_UNSUBSCRIBE_EN = "Unsubscribe ➖";
    public static final String MENU_UNSUBSCRIBE_RU = "Отписаться ➖";
    public static final String MENU_LIST_HY = "Իմ բաժանորդագրությունները 📋";
    public static final String MENU_LIST_EN = "My subscriptions 📋";
    public static final String MENU_LIST_RU = "Мои подписки 📋";
    public static final String MENU_LANGUAGE_HY = "Փոխել լեզուն 🌐";
    public static final String MENU_LANGUAGE_EN = "Change language 🌐";
    public static final String MENU_LANGUAGE_RU = "Изменить язык 🌐";
    public static final String MENU_SUPPORT_HY = "Աջակցություն 💬";
    public static final String MENU_SUPPORT_EN = "Support 💬";
    public static final String MENU_SUPPORT_RU = "Поддержка 💬";

    public static String menuSubscribeLabel(Language lang) {
        return text(lang, MENU_SUBSCRIBE_HY, MENU_SUBSCRIBE_EN, MENU_SUBSCRIBE_RU);
    }

    public static String menuUnsubscribeLabel(Language lang) {
        return text(lang, MENU_UNSUBSCRIBE_HY, MENU_UNSUBSCRIBE_EN, MENU_UNSUBSCRIBE_RU);
    }

    public static String menuListLabel(Language lang) {
        return text(lang, MENU_LIST_HY, MENU_LIST_EN, MENU_LIST_RU);
    }

    public static String menuLanguageLabel(Language lang) {
        return text(lang, MENU_LANGUAGE_HY, MENU_LANGUAGE_EN, MENU_LANGUAGE_RU);
    }

    public static String menuSupportLabel(Language lang) {
        return text(lang, MENU_SUPPORT_HY, MENU_SUPPORT_EN, MENU_SUPPORT_RU);
    }

    /** Shown by /support (or the Support menu button) while we wait for the user's next message. */
    public static String supportPrompt(Language lang) {
        return text(lang,
                """
                Գրեք Ձեր առաջարկը, կամ նկարագրեք սխալը, որը գտել եք բոտում։ Ձեր հաղորդագրությունը կփոխանցվի բոտի ստեղծողին։

                Չեղարկելու համար պարզապես օգտագործեք ներքևի կոճակները։""",
                """
                Please write your suggestion, or describe the bug you found in the bot. Your message will be passed on to the bot's developer.

                To cancel, just use the menu buttons below.""",
                """
                Напишите ваше предложение или опишите ошибку, которую вы нашли в боте. Ваше сообщение будет передано разработчику бота.

                Чтобы отменить, просто используйте кнопки меню внизу.""");
    }

    public static String supportReceived(Language lang) {
        return text(lang,
                "Շնորհակալությո՛ւն։ Ձեր հաղորդագրությունը ուղարկվեց ստեղծողին։",
                "Thank you! Your message has been sent to the developer.",
                "Спасибо! Ваше сообщение отправлено разработчику.");
    }

    // ---- Admin-only: support messages and replies to them ----

    public static String adminSupportMessageHeader(Language lang, String user) {
        return text(lang,
                "💬 Նոր հաղորդագրություն աջակցությանը։ Ուղարկող՝ " + user,
                "💬 Support message from " + user + ":",
                "💬 Сообщение в поддержку от " + user + ":");
    }

    public static String buttonAdminReply(Language lang) {
        return text(lang, "↩️ Պատասխանել", "↩️ Reply", "↩️ Ответить");
    }

    public static String adminReplyPrompt(Language lang, String user) {
        return text(lang,
                "✍️ Գրեք Ձեր պատասխանը։ Ստացող՝ " + user + "։ Ուղարկելուց առաջ բոտը կխնդրի հաստատել։\n\n"
                        + "Չեղարկելու համար օգտագործեք ցանկացած կոճակ կամ հրաման։",
                "✍️ Write your reply to " + user + ". You'll be asked to confirm before it's sent.\n\n"
                        + "To cancel, use any menu button or command.",
                "✍️ Напишите ответ пользователю " + user + ". Перед отправкой бот попросит подтверждение.\n\n"
                        + "Чтобы отменить, используйте любую кнопку меню или команду.");
    }

    public static String adminReplyTooLong(Language lang, int length, int max) {
        return text(lang,
                "Պատասխանը չափազանց երկար է (" + length + " նիշ, առավելագույնը՝ " + max + ")։ Ուղարկեք ավելի կարճ տարբերակ։",
                "That reply is too long (" + length + " characters, max " + max + "). Please send a shorter one.",
                "Ответ слишком длинный (" + length + " символов, максимум " + max + "). Отправьте покороче.");
    }

    public static String adminReplyConfirm(Language lang, String user) {
        return text(lang,
                "Իսկապե՞ս ուղարկել այս հաղորդագրությունը։ Ստացող՝ " + user,
                "Do you really want to send this to " + user + "?",
                "Вы действительно хотите отправить это пользователю " + user + "?");
    }

    public static String buttonAdminReplySend(Language lang) {
        return text(lang, "✅ Այո, ուղարկել", "✅ Yes, send", "✅ Да, отправить");
    }

    public static String buttonAdminReplyCancel(Language lang) {
        return text(lang, "❌ Չեղարկել", "❌ Cancel", "❌ Отмена");
    }

    public static String adminReplySent(Language lang, String user) {
        return text(lang,
                "✅ Ուղարկվեց։ Ստացող՝ " + user,
                "✅ Sent to " + user + ".",
                "✅ Отправлено пользователю " + user + ".");
    }

    public static String adminReplyFailed(Language lang, String user) {
        return text(lang,
                "⚠️ Չհաջողվեց ուղարկել։ Ստացող՝ " + user + "։ Հնարավոր է՝ նա արգելափակել է բոտը։",
                "⚠️ Couldn't deliver to " + user + " — they may have blocked the bot.",
                "⚠️ Не удалось доставить пользователю " + user + " — возможно, он заблокировал бота.");
    }

    public static String adminReplyCancelled(Language lang) {
        return text(lang,
                "Չեղարկվեց․ ոչինչ չուղարկվեց։",
                "Cancelled — nothing was sent.",
                "Отменено — ничего не отправлено.");
    }

    public static String adminReplyGone(Language lang) {
        return text(lang,
                "Այս պատասխանն արդեն ուղարկվել կամ չեղարկվել է։",
                "This reply was already sent or cancelled.",
                "Этот ответ уже отправлен или отменён.");
    }

    /** Heading above the admin's answer to a user's /support message. */
    public static String supportReplyHeader(Language lang) {
        return text(lang,
                "💬 Պատասխան բոտի ստեղծողից՝",
                "💬 Reply from the bot's developer:",
                "💬 Ответ от разработчика бота:");
    }

    /** Shown when the user sends an empty/whitespace-only support message. */
    public static String supportEmpty(Language lang) {
        return text(lang,
                "Հաղորդագրությունը դատարկ է։ Կրկին փորձեք /support հրամանով։",
                "That message was empty. Please try again with /support.",
                "Сообщение пустое. Попробуйте ещё раз с помощью /support.");
    }

    public static String menuTitle(Language lang) {
        return text(lang, "Ընտրացանկ.", "Menu:", "Меню:");
    }

    public static String askKeywordToSubscribe(Language lang) {
        return text(lang,
                "Գրեք այն փողոցի կամ թաղամասի անվանումը, որին ցանկանում եք բաժանորդագրվել։",
                "Send me the street or district name you want to subscribe to.",
                "Напишите название улицы или района, на которое хотите подписаться.");
    }

    public static String chooseRegionPrompt(Language lang) {
        return text(lang,
                "Ընտրեք մարզը, որի ջրանջատումների մասին ցանկանում եք տեղեկանալ.",
                "Choose the region you want outage notifications for:",
                "Выберите регион, о котором хотите получать уведомления:");
    }

    public static String chooseDistrictPrompt(Language lang) {
        return text(lang,
                "Ընտրեք Երևանի վարչական շրջանը.",
                "Choose a district of Yerevan:",
                "Выберите административный район Еревана:");
    }

    /** Shown after a region/district is picked, right above the "whole area" / "specific street" buttons. */
    public static String scopeChosenPrompt(Language lang, String scopeDisplayName) {
        return text(lang,
                "Ընտրվեց՝ «" + scopeDisplayName + "»։ Ի՞նչ եք ցանկանում.",
                "You picked \"" + scopeDisplayName + "\". What would you like?",
                "Вы выбрали «" + scopeDisplayName + "». Что вы хотите?");
    }

    public static final String BUTTON_WHOLE_REGION_HY = "Ամբողջ մարզը ✅";
    public static final String BUTTON_WHOLE_REGION_EN = "Whole region ✅";
    public static final String BUTTON_WHOLE_REGION_RU = "Весь регион ✅";
    public static final String BUTTON_WHOLE_DISTRICT_HY = "Ամբողջ թաղամասը ✅";
    public static final String BUTTON_WHOLE_DISTRICT_EN = "Whole district ✅";
    public static final String BUTTON_WHOLE_DISTRICT_RU = "Весь район ✅";
    public static final String BUTTON_ENTER_STREET_HY = "Նշել փողոցի անվանումը ✍️";
    public static final String BUTTON_ENTER_STREET_EN = "Enter street name ✍️";
    public static final String BUTTON_ENTER_STREET_RU = "Указать название улицы ✍️";

    public static String buttonWholeRegion(Language lang) {
        return text(lang, BUTTON_WHOLE_REGION_HY, BUTTON_WHOLE_REGION_EN, BUTTON_WHOLE_REGION_RU);
    }

    public static String buttonWholeDistrict(Language lang) {
        return text(lang, BUTTON_WHOLE_DISTRICT_HY, BUTTON_WHOLE_DISTRICT_EN, BUTTON_WHOLE_DISTRICT_RU);
    }

    public static String buttonEnterStreet(Language lang) {
        return text(lang, BUTTON_ENTER_STREET_HY, BUTTON_ENTER_STREET_EN, BUTTON_ENTER_STREET_RU);
    }

    /** The last segment of a "whole region/district" subscription's display label — see VeoliaNotifierBot#describeSubscription. */
    public static String allLabel(Language lang) {
        return text(lang, "Բոլորը", "All", "Все");
    }

    public static final String BUTTON_GO_BACK_HY = "Հետ ⬅️";
    public static final String BUTTON_GO_BACK_EN = "Go back ⬅️";
    public static final String BUTTON_GO_BACK_RU = "Назад ⬅️";

    public static String buttonGoBack(Language lang) {
        return text(lang, BUTTON_GO_BACK_HY, BUTTON_GO_BACK_EN, BUTTON_GO_BACK_RU);
    }

    /** Shown once a region/district scope is set, asking the user to type the street name. */
    public static String askStreetNameInScope(Language lang, String scopeDisplayName) {
        return text(lang,
                "Գրեք փողոցի անվանումը (" + scopeDisplayName + ")։",
                "Send me the street name (in " + scopeDisplayName + ").",
                "Напишите название улицы (в «" + scopeDisplayName + "»).");
    }

    public static String subscribedWholeScope(Language lang, String scopeDisplayName) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք «" + scopeDisplayName + "»-ի բոլոր ջրանջատումներին։",
                "Subscribed to all outages in \"" + scopeDisplayName + "\".",
                "Вы подписались на все отключения в «" + scopeDisplayName + "».");
    }

    public static String alreadySubscribedWholeScope(Language lang, String scopeDisplayName) {
        return text(lang,
                "Դուք արդեն բաժանորդագրված եք «" + scopeDisplayName + "»-ի բոլոր ջրանջատումներին։",
                "You're already subscribed to all outages in \"" + scopeDisplayName + "\".",
                "Вы уже подписаны на все отключения в «" + scopeDisplayName + "».");
    }

    public static String subscribedScopedStreet(Language lang, String scopeDisplayName, String keyword) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք՝ «" + keyword + "» (" + scopeDisplayName + ")։",
                "Subscribed to \"" + keyword + "\" (" + scopeDisplayName + ").",
                "Вы подписались на «" + keyword + "» (" + scopeDisplayName + ").");
    }

    /** Used when the stored keyword differs from what the user typed (transliterated to Armenian). */
    public static String subscribedScopedStreetTransliterated(Language lang, String scopeDisplayName,
                                                                String original, String armenian) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք՝ «" + armenian + "» (" + scopeDisplayName + ", փոխադրվեց «" + original + "»-ից)։\n\n"
                        + "Նկատի ունեցեք. «Վեոլիա Ջուր»-ի հայտարարությունները հրապարակվում են միայն "
                        + "հայերենով, ուստի Ձեր բառը ավտոմատ կերպով փոխադրվեց հայերենի։ Համընկնումը "
                        + "ստուգելիս թույլատրվում է մինչև 2 տառի տարբերություն։",
                "Subscribed to \"" + armenian + "\" (" + scopeDisplayName + ", converted from \"" + original + "\").\n\n"
                        + "Please note: Veolia Jur only shares announcements in Armenian, so we "
                        + "automatically converted your word to Armenian. We'll allow up to 2 letter "
                        + "differences when matching it against future posts.",
                "Вы подписались на «" + armenian + "» (" + scopeDisplayName + ", преобразовано из «" + original + "»).\n\n"
                        + "Обратите внимание: «Veolia Jur» публикует объявления только на армянском "
                        + "языке, поэтому ваше слово было автоматически преобразовано в армянский. При "
                        + "поиске совпадений допускается расхождение до 2 букв.");
    }

    public static String alreadySubscribedScopedStreet(Language lang, String scopeDisplayName, String keyword) {
        return text(lang,
                "Դուք արդեն բաժանորդագրված եք՝ «" + keyword + "» (" + scopeDisplayName + ")։",
                "You're already subscribed to \"" + keyword + "\" (" + scopeDisplayName + ").",
                "Вы уже подписаны на «" + keyword + "» (" + scopeDisplayName + ").");
    }

    public static String chooseKeywordToUnsubscribe(Language lang) {
        return text(lang,
                "Ընտրեք, թե որ բաժանորդագրությունը ցանկանում եք չեղարկել.",
                "Select which subscription to remove:",
                "Выберите, какую подписку удалить:");
    }

    /**
     * The bot's profile description (Telegram's "What can this bot do?", shown in an empty chat
     * before the user presses Start). Telegram caps it at 512 characters.
     */
    public static String botDescription(Language lang) {
        return text(lang,
                """
                Այս բոտը Ձեզ տեղեկացնում է Ձեր փողոցում կամ թաղամասում «Վեոլիա Ջուր»-ի \
                հայտարարած ջրանջատումների մասին։

                Սա «Վեոլիա Ջուր»-ի պաշտոնական բոտը չէ և կապ չունի ընկերության հետ․ բոտը միայն \
                կարդում է «Վեոլիա Ջուր»-ի հանրային Telegram ալիքը։ Ջրի, վթարների կամ հաշիվների \
                հարցերով դիմեք անմիջապես «Վեոլիա Ջուր»-ին։""",
                """
                This bot notifies you about water outages announced by Veolia Jur for your \
                street or district.

                It is not an official Veolia Jur bot and is not affiliated with the company — \
                the bot only reads Veolia Jur's public Telegram channel. For questions about \
                water supply, accidents or bills, please contact Veolia Jur directly.""",
                """
                Этот бот уведомляет вас об отключениях воды на вашей улице или в вашем районе, \
                объявленных «Veolia Jur».

                Это не официальный бот «Veolia Jur», и он не связан с компанией — бот лишь \
                читает публичный Telegram-канал «Veolia Jur». По вопросам водоснабжения, аварий \
                или счетов обращайтесь напрямую в «Veolia Jur».""");
    }

    /** The bot's profile "About" line, also shown when the bot is shared. Telegram caps it at 120 characters. */
    public static String botShortDescription(Language lang) {
        return text(lang,
                "Ծանուցումներ «Վեոլիա Ջուր»-ի ջրանջատումների մասին՝ Ձեր հասցեով։ Պաշտոնական բոտ չէ։",
                "Alerts about Veolia Jur water outages at your address. Not an official Veolia Jur bot.",
                "Уведомления об отключениях воды «Veolia Jur» по вашему адресу. Не официальный бот компании.");
    }

    private static String text(Language lang, String hy, String en, String ru) {
        Map<Language, String> map = new EnumMap<>(Language.class);
        map.put(Language.HY, hy);
        map.put(Language.EN, en);
        map.put(Language.RU, ru);
        return map.getOrDefault(lang, hy);
    }
}

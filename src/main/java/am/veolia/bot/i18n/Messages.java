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

                Բաժանորդագրվեք Ձեզ հետաքրքրող փողոցի կամ թաղամասի անվանը, և ես կտեղեկացնեմ Ձեզ, \
                երբ նոր հայտարարություն հայտնվի այդ հասցեի վերաբերյալ։ Կարող եք գրել լատինատառ \
                կամ ռուսերեն տառերով՝ ես կփորձեմ վերածել հայերենի։

                Օգտագործեք ներքևի կոճակները, կամ այս հրամանները.
                /subscribe <բառ> — բաժանորդագրվել
                /unsubscribe <բառ> — չեղարկել բաժանորդագրությունը
                /list — ցուցադրել բաժանորդագրությունները
                /language — փոխել լեզուն
                /help — օգնություն""",
                """
                Hi! I send notifications about water outage announcements from Veolia Jur.

                Subscribe to a street or district name you care about, and I'll notify you \
                whenever a new announcement mentions it. You can type it in English or Russian \
                letters — I'll try to convert it to Armenian automatically.

                Use the buttons below, or these commands:
                /subscribe <keyword> — add a subscription
                /unsubscribe <keyword> — remove a subscription
                /list — show your subscriptions
                /language — change language
                /help — show help""",
                """
                Привет! Я отправляю уведомления об отключениях воды от «Veolia Jur».

                Подпишитесь на интересующую вас улицу или район, и я сообщу вам, как только \
                появится новое объявление об этом адресе. Можно писать латиницей или \
                по-русски — я постараюсь автоматически преобразовать это в армянский.

                Используйте кнопки внизу или эти команды:
                /subscribe <слово> — добавить подписку
                /unsubscribe <слово> — удалить подписку
                /list — показать ваши подписки
                /language — изменить язык
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
                /help — այս հաղորդագրությունը""",
                """
                You can use the buttons below, or these commands:
                /subscribe <keyword> — subscribe to a street or district name
                /unsubscribe <keyword> — remove a subscription
                /list — show your current subscriptions
                /language — change language
                /menu — show the button menu
                /help — show this message""",
                """
                Можно использовать кнопки внизу или эти команды:
                /subscribe <слово> — подписаться на улицу или район
                /unsubscribe <слово> — удалить подписку
                /list — показать текущие подписки
                /language — изменить язык
                /menu — показать меню кнопок
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

    private static String text(Language lang, String hy, String en, String ru) {
        Map<Language, String> map = new EnumMap<>(Language.class);
        map.put(Language.HY, hy);
        map.put(Language.EN, en);
        map.put(Language.RU, ru);
        return map.getOrDefault(lang, hy);
    }
}

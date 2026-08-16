package am.veolia.bot.i18n;

import am.veolia.bot.model.Language;

import java.util.EnumMap;
import java.util.Map;

/**
 * All bot-authored UI text (greetings, confirmations, errors, help) in both
 * supported languages. The outage announcement text itself is never
 * translated here — it's forwarded to users verbatim in its original Armenian.
 */
public final class Messages {

    private Messages() {
    }

    public static final String CHOOSE_LANGUAGE_HY = "Խնդրում ենք ընտրել լեզուն.";
    public static final String CHOOSE_LANGUAGE_EN = "Please choose a language.";

    public static String chooseLanguagePrompt() {
        return CHOOSE_LANGUAGE_HY + "\n" + CHOOSE_LANGUAGE_EN;
    }

    public static String welcome(Language lang) {
        return text(lang,
                """
                Բարև ձեզ։ Ես ծանուցումներ եմ ուղարկում «Վեոլիա Ջուր» ընկերության ջրանջատումների \
                մասին։

                Բաժանորդագրվեք Ձեզ հետաքրքրող փողոցի կամ թաղամասի անվանը, և ես կտեղեկացնեմ Ձեզ, \
                երբ նոր հայտարարություն հայտնվի այդ հասցեի վերաբերյալ։

                Օգտագործեք ներքևի կոճակները, կամ այս հրամանները.
                /subscribe <բառ> — բաժանորդագրվել
                /unsubscribe <բառ> — չեղարկել բաժանորդագրությունը
                /list — ցուցադրել բաժանորդագրությունները
                /language — փոխել լեզուն
                /help — օգնություն""",
                """
                Hi! I send notifications about water outage announcements from Veolia Jur.

                Subscribe to a street or district name you care about, and I'll notify you \
                whenever a new announcement mentions it.

                Use the buttons below, or these commands:
                /subscribe <keyword> — add a subscription
                /unsubscribe <keyword> — remove a subscription
                /list — show your subscriptions
                /language — change language
                /help — show help"""
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
                /help — show this message"""
        );
    }

    public static String languageUpdated(Language lang) {
        return text(lang, "Լեզուն փոփոխվել է հայերենի։", "Language set to English.");
    }

    public static String subscribeUsage(Language lang) {
        return text(lang, "Օգտագործում. /subscribe <բառ>", "Usage: /subscribe <keyword>");
    }

    public static String subscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք բաժանորդագրվեցիք՝ «" + keyword + "»։",
                "Subscribed to \"" + keyword + "\".");
    }

    public static String alreadySubscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք արդեն բաժանորդագրված եք՝ «" + keyword + "»։",
                "You're already subscribed to \"" + keyword + "\".");
    }

    public static String unsubscribeUsage(Language lang) {
        return text(lang, "Օգտագործում. /unsubscribe <բառ>", "Usage: /unsubscribe <keyword>");
    }

    public static String unsubscribed(Language lang, String keyword) {
        return text(lang,
                "Բաժանորդագրությունը՝ «" + keyword + "», չեղարկվեց։",
                "Unsubscribed from \"" + keyword + "\".");
    }

    public static String notSubscribed(Language lang, String keyword) {
        return text(lang,
                "Դուք բաժանորդագրված չեք «" + keyword + "»-ին։",
                "You're not subscribed to \"" + keyword + "\".");
    }

    public static String listEmpty(Language lang) {
        return text(lang,
                "Դուք դեռ ոչ մի բաժանորդագրություն չունեք։ Օգտագործեք /subscribe <բառ>։",
                "You have no subscriptions yet. Use /subscribe <keyword>.");
    }

    public static String listHeader(Language lang) {
        return text(lang, "Ձեր բաժանորդագրությունները.", "Your subscriptions:");
    }

    public static String unknownCommand(Language lang) {
        return text(lang,
                "Անհայտ հրաման։ Օգտագործեք /help՝ հրամանների ցանկը տեսնելու համար։",
                "Unknown command. Use /help to see available commands.");
    }

    public static String newOutageHeader(Language lang) {
        return text(lang, "🚨 Նոր ջրանջատում՝ ըստ Ձեր բաժանորդագրության.", "🚨 New outage matching your subscription:");
    }

    // Persistent reply-keyboard menu button labels. Kept as public constants (not
    // just accessor methods) because incoming button-press messages need to be
    // matched against these exact strings regardless of the user's current language.
    public static final String MENU_SUBSCRIBE_HY = "/Բաժանորդագրվել ➕";
    public static final String MENU_SUBSCRIBE_EN = "/Subscribe ➕";
    public static final String MENU_UNSUBSCRIBE_HY = "/Չեղարկել բաժանորդագրությունը ➖";
    public static final String MENU_UNSUBSCRIBE_EN = "/Unsubscribe ➖";
    public static final String MENU_LIST_HY = "/Իմ բաժանորդագրությունները 📋";
    public static final String MENU_LIST_EN = "/My subscriptions 📋";
    public static final String MENU_LANGUAGE_HY = "/Փոխել լեզուն 🌐";
    public static final String MENU_LANGUAGE_EN = "/Change language 🌐";

    public static String menuSubscribeLabel(Language lang) {
        return text(lang, MENU_SUBSCRIBE_HY, MENU_SUBSCRIBE_EN);
    }

    public static String menuUnsubscribeLabel(Language lang) {
        return text(lang, MENU_UNSUBSCRIBE_HY, MENU_UNSUBSCRIBE_EN);
    }

    public static String menuListLabel(Language lang) {
        return text(lang, MENU_LIST_HY, MENU_LIST_EN);
    }

    public static String menuLanguageLabel(Language lang) {
        return text(lang, MENU_LANGUAGE_HY, MENU_LANGUAGE_EN);
    }

    public static String menuTitle(Language lang) {
        return text(lang, "Ընտրացանկ.", "Menu:");
    }

    public static String askKeywordToSubscribe(Language lang) {
        return text(lang,
                "Գրեք այն փողոցի կամ թաղամասի անվանումը, որին ցանկանում եք բաժանորդագրվել։",
                "Send me the street or district name you want to subscribe to.");
    }

    public static String chooseKeywordToUnsubscribe(Language lang) {
        return text(lang,
                "Ընտրեք, թե որ բաժանորդագրությունը ցանկանում եք չեղարկել.",
                "Select which subscription to remove:");
    }

    private static String text(Language lang, String hy, String en) {
        Map<Language, String> map = new EnumMap<>(Language.class);
        map.put(Language.HY, hy);
        map.put(Language.EN, en);
        return map.getOrDefault(lang, hy);
    }
}

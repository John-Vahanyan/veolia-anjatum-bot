package am.veolia.bot.model;

/** UI language a user has chosen for the bot's own messages (not the outage text itself). */
public enum Language {
    HY,
    EN,
    RU;

    public static Language fromCode(String code) {
        if (code == null) {
            return HY;
        }
        try {
            return Language.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HY;
        }
    }
}

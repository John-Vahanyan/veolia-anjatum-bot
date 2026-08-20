package am.veolia.bot.model;

/**
 * The 11 first-level administrative divisions of Armenia (Yerevan, the
 * capital, plus the 10 {@code մարզեր}/"regions" that cover the rest of the
 * country) — drives the region-picker step of the guided {@code /subscribe}
 * flow.
 *
 * <p>{@link #armenian()} is the only form ever compared against outage post
 * text: Veolia Jur posts exclusively in Armenian, and every non-Yerevan
 * post's title names its region in the genitive case immediately followed
 * by "մարզի" (e.g. {@code "Կոտայքի մարզի Բլահովիտ գյուղում"}), so a plain
 * substring check of the base form against that text is enough — no fuzzy
 * matching needed since the name always comes from this fixed list, never
 * user-typed. {@link #english()} and {@link #russian()} exist purely for
 * the buttons/messages shown to the user.
 */
public enum Region {
    YEREVAN("Երևան", "Yerevan", "Ереван"),
    ARAGATSOTN("Արագածոտն", "Aragatsotn", "Арагацотн"),
    ARARAT("Արարատ", "Ararat", "Арарат"),
    ARMAVIR("Արմավիր", "Armavir", "Армавир"),
    GEGHARKUNIK("Գեղարքունիք", "Gegharkunik", "Гегаркуник"),
    KOTAYK("Կոտայք", "Kotayk", "Котайк"),
    LORI("Լոռի", "Lori", "Лори"),
    SHIRAK("Շիրակ", "Shirak", "Ширак"),
    SYUNIK("Սյունիք", "Syunik", "Сюник"),
    VAYOTS_DZOR("Վայոց ձոր", "Vayots Dzor", "Вайоц Дзор"),
    TAVUSH("Տավուշ", "Tavush", "Тавуш");

    private final String armenian;
    private final String english;
    private final String russian;

    Region(String armenian, String english, String russian) {
        this.armenian = armenian;
        this.english = english;
        this.russian = russian;
    }

    /** The canonical Armenian base form — what gets matched against post text. */
    public String armenian() {
        return armenian;
    }

    /** The name shown to the user, in their chosen UI language. */
    public String displayName(Language lang) {
        return switch (lang) {
            case EN -> english;
            case RU -> russian;
            case HY -> armenian;
        };
    }

    /** Looks up a region by {@link #name()}, or {@code null} if {@code code} doesn't match one. */
    public static Region fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Region.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

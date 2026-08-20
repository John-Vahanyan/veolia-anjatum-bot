package am.veolia.bot.model;

/**
 * The 12 administrative districts ({@code վարչական շրջաններ}) of Yerevan —
 * drives the district-picker step of the guided {@code /subscribe} flow,
 * shown only after the user picks {@link Region#YEREVAN}.
 *
 * <p>{@link #armenian()} is the only form ever compared against outage post
 * text: every Yerevan post's title names its district in the form
 * {@code "Երևանի <District> վարչական շրջանում"}, so a plain substring check
 * of the base form is enough — see {@link Region} for the same reasoning.
 */
public enum District {
    AJAPNYAK("Աջափնյակ", "Ajapnyak", "Аджапняк"),
    AVAN("Ավան", "Avan", "Аван"),
    ARABKIR("Արաբկիր", "Arabkir", "Арабкир"),
    DAVTASHEN("Դավթաշեն", "Davtashen", "Давташен"),
    EREBUNI("Էրեբունի", "Erebuni", "Эребуни"),
    KANAKER_ZEYTUN("Քանաքեռ-Զեյթուն", "Kanaker-Zeytun", "Канакер-Зейтун"),
    KENTRON("Կենտրոն", "Kentron", "Кентрон"),
    MALATIA_SEBASTIA("Մալաթիա-Սեբաստիա", "Malatia-Sebastia", "Малатия-Себастия"),
    NOR_NORK("Նոր Նորք", "Nor Nork", "Нор Норк"),
    NORK_MARASH("Նորք-Մարաշ", "Nork-Marash", "Норк-Мараш"),
    NUBARASHEN("Նուբարաշեն", "Nubarashen", "Нубарашен"),
    SHENGAVIT("Շենգավիթ", "Shengavit", "Шенгавит");

    private final String armenian;
    private final String english;
    private final String russian;

    District(String armenian, String english, String russian) {
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

    /** Looks up a district by {@link #name()}, or {@code null} if {@code code} doesn't match one. */
    public static District fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return District.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

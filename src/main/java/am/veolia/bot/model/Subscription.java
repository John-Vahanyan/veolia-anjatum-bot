package am.veolia.bot.model;

/**
 * @param keyword      the text matched against announcement fragments — a
 *                      street name, or (for a "whole region/district"
 *                      subscription) the region/district's own Armenian name.
 * @param regionCode   {@link Region#name()} the subscription is scoped to, or
 *                      {@code ""} if it isn't (legacy free-text subscriptions).
 * @param districtCode {@link District#name()} the subscription is scoped to, or
 *                      {@code ""} if it isn't (non-Yerevan or legacy subscriptions).
 * @param type         how {@code keyword} should be matched — see {@link SubscriptionType}.
 * @param fuzzyMatch   whether {@code keyword} may be matched with up to 2 letters of edit-distance
 *                      tolerance (see {@code KeywordMatcher}). True only when the user typed the
 *                      keyword in Latin/Cyrillic letters and it was auto-transliterated to
 *                      Armenian — that conversion is a best-effort guess, so some slack is
 *                      warranted. A keyword typed directly in Armenian is taken at face value and
 *                      must match exactly, to avoid firing on an unrelated, similarly-spelled
 *                      street. Irrelevant (stored false) for a "whole region/district" keyword,
 *                      since that text is a canonical name picked via button, never typed.
 */
public record Subscription(long id, long userId, String keyword, String regionCode, String districtCode,
                            SubscriptionType type, boolean fuzzyMatch) {

    public Region region() {
        return Region.fromCode(regionCode);
    }

    public District district() {
        return District.fromCode(districtCode);
    }
}

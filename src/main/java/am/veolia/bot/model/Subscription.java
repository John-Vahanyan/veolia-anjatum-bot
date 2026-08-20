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
 */
public record Subscription(long id, long userId, String keyword, String regionCode, String districtCode,
                            SubscriptionType type) {

    public Region region() {
        return Region.fromCode(regionCode);
    }

    public District district() {
        return District.fromCode(districtCode);
    }
}

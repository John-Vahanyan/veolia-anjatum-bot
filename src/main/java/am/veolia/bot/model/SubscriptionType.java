package am.veolia.bot.model;

/**
 * How a {@link Subscription}'s {@code keyword} should be matched against an
 * announcement — see {@link am.veolia.bot.parser.KeywordMatcher}.
 */
public enum SubscriptionType {
    /**
     * Plain keyword match against every fragment (district + all streets) of
     * an announcement, with no geographic scoping. Covers both the legacy
     * free-text {@code /subscribe <keyword>} path and the guided flow's
     * "whole region"/"whole district" choices — in the latter case the
     * keyword itself already *is* the region/district name, so a plain
     * match against the district fragment is exactly the right behavior.
     */
    KEYWORD,

    /**
     * A street name entered after narrowing down to a specific
     * {@link Region} or {@link District} in the guided flow. Requires
     * *both* the announcement's district fragment to match the chosen
     * scope *and* one of its street fragments to match the keyword —
     * this is what stops a street subscription from firing on a
     * similarly-spelled street in a completely different part of the
     * country (the original complaint that motivated adding scoping).
     */
    SCOPED_STREET
}

package am.veolia.bot.parser;

import am.veolia.bot.model.ChannelPost;
import am.veolia.bot.model.OutageAnnouncement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutageAnnouncementParserTest {

    private final OutageAnnouncementParser parser = new OutageAnnouncementParser();

    @Test
    void parsesTheCanonicalShengavitExample() {
        String text = """
                Վթարային ջրանջատում Երևանի Շենգավիթ վարչական շրջանում օգոստոսի 16-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է իր հաճախորդներին և սպառողներին, որ վթարային
                աշխատանքներով պայմանավորված ս.թ օգոստոսի 16-ին ժամը 14:00-16:00-ն կդադարեցվի
                Մանթաշյան 6-12 զույգ համարի շենքերի ջրամատակարարումը:
                Ընկերությունը հայցում է սպառողների ներողամտությունը պատճառված անհանգստության
                և կանխավ շնորհակալություն հայտնում ըմբռնման համար:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1001", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        OutageAnnouncement announcement = result.get();
        assertThat(announcement.postId()).isEqualTo("VeoliaJur/1001");
        assertThat(announcement.district()).isEqualTo("Երևանի Շենգավիթ վարչական շրջանում");
        assertThat(announcement.date()).isEqualTo("օգոստոսի 16");
        assertThat(announcement.startTime()).isEqualTo("14:00");
        assertThat(announcement.endTime()).isEqualTo("16:00");
        assertThat(announcement.streets()).containsExactly("Մանթաշյան 6-12 զույգ համարի շենքերի");
        assertThat(announcement.rawText()).isEqualTo(text);
    }

    @Test
    void parsesMultipleCommaSeparatedStreets() {
        String text = """
                Վթարային ջրանջատում Երևանի Կենտրոն վարչական շրջանում սեպտեմբերի 3-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ սեպտեմբերի 3-ին ժամը 10:00-13:00-ն կդադարեցվի Թումանյան 10, Սայաթ-Նովա 5
                և Աբովյան 22 ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1002", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().streets()).containsExactly("Թումանյան 10", "Սայաթ-Նովա 5", "Աբովյան 22");
    }

    @Test
    void parsesAnOtherCityAnnouncement() {
        String text = """
                Վթարային ջրանջատում Աբովյան քաղաքում հունվարի 5-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ հունվարի 5-ին ժամը 09:00-12:00-ն կդադարեցվի Երևանյան փողոցի ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1003", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().district()).isEqualTo("Աբովյան քաղաքում");
        assertThat(result.get().streets()).containsExactly("Երևանյան փողոցի");
    }

    @Test
    void parsesAnOpenEndedTimeWithNoEndTime() {
        String text = """
                Վթարային ջրանջատում Երևանի Արաբկիր վարչական շրջանում մարտի 2-ին

                «Վեոլիա Ջուր» ընկերությունը տեղեկացնում է, որ վթարային աշխատանքներով պայմանավորված
                ս.թ մարտի 2-ին ժամը 08:30-ից կդադարեցվի Կոմիտասի պողոտա 40 ջրամատակարարումը:""";
        ChannelPost post = new ChannelPost("VeoliaJur/1004", text);

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isPresent();
        assertThat(result.get().startTime()).isEqualTo("08:30");
        assertThat(result.get().endTime()).isNull();
        assertThat(result.get().streets()).containsExactly("Կոմիտասի պողոտա 40");
    }

    @Test
    void skipsUnrelatedPostsRatherThanThrowing() {
        ChannelPost post = new ChannelPost("VeoliaJur/1005", "Շնորհավոր Նոր տարի բոլորիդ:");

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsPostsMissingTheTimeStreetsClause() {
        ChannelPost post = new ChannelPost("VeoliaJur/1006",
                "Վթարային ջրանջատում Երևանի Նոր Նորք վարչական շրջանում մայիսի 1-ին\n\n"
                        + "Աշխատանքները կավարտվեն մոտակա օրերին:");

        Optional<OutageAnnouncement> result = parser.parse(post);

        assertThat(result).isEmpty();
    }
}

package org.omnaest.wiki;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;
import org.omnaest.utils.JSONHelper;
import org.omnaest.wiki.MicroCrawler.Matchers;

public class MicroCrawlerTest
{
    private MicroCrawler crawler = MicroCrawler.newInstance();

    @Test
    //    @Ignore
    public void testAnalyze() throws Exception
    {
        this.crawler.addMatcher(Matchers.EMAIL)
                    .analyze("http://www.uk.rub.de/lehre/index.html.de")
                    .getMatches()
                    .forEach(match ->
                    {
                        System.out.println(JSONHelper.prettyPrint(match));
                    });
    }

    @Test
    public void testMatchers()
    {
        assertTrue(Pattern.compile(Matchers.EMAIL.get())
                          .matcher("E-Mail: regine.zimmermann@elisabethgruppe.de")
                          .find());
    }

}

/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
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

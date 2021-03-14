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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.repository.nitrite.NitriteRepositoryUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLFilters;

public class WikiUtilsTest
{

    @Test
    @Ignore
    public void test() throws Exception
    {
        WikiUtils.newInstance()
                 .connectToWikiDataAndWikipedia()
                 .usingLocalCache()
                 .searchFor(SPARQLFilters.INSTANCE_OF_MEDICAL_RESEARCH_INSTITUTE)
                 .stream()
                 .limit(100)
                 .forEach(item ->
                 {
                     System.out.println("------------------------");
                     item.getTitle()
                         .ifPresent(System.out::println);
                     item.getDescription()
                         .ifPresent(description -> System.out.println("  Description: " + description));
                     item.getHomePage()
                         .ifPresent(link -> System.out.println("  Homepage: " + link));
                     item.getCountry()
                         .ifPresent(country -> System.out.println("  Country: " + country));
                     item.getCity()
                         .ifPresent(city -> System.out.println("  City: " + city));

                     //                     String text = item.resolveText();
                     //                     System.out.println(text);
                     //                     System.out.println();
                 });

    }

    @Test
    @Ignore
    public void testPersonCache() throws Exception
    {
        Cache cache = NitriteRepositoryUtils.newLocalCache("persons");
        AtomicInteger counter = new AtomicInteger();
        cache.keySet()
             .forEach(name ->
             {
                 System.out.println(counter.incrementAndGet() + ":" + name);
             });
    }

    @Test
    @Ignore
    public void testPerson() throws Exception
    {
        Cache cache = NitriteRepositoryUtils.newLocalCache("persons");

        AtomicInteger counter = new AtomicInteger();

        WikiUtils.newInstance()
                 .connectToWikiDataAndWikipedia()
                 .usingLocalCache()
                 .searchFor(SPARQLFilters.INSTANCE_OF_HUMAN)
                 .stream()
                 .skip(800000)
                 //                 .limit(100)
                 .forEach(item ->
                 {
                     System.out.println("----------- " + counter.incrementAndGet() + " -------------");
                     item.getTitle()
                         .ifPresent(t ->
                         {
                             String homepage = item.getHomePage()
                                                   .orElse(null);
                             System.out.println(t + " : " + homepage);
                             cache.put(t, homepage);
                         });
                 });

    }

    @Test
    @Ignore
    public void testResearchInstitute() throws Exception
    {
        Cache cache = NitriteRepositoryUtils.newLocalCache("hospital");

        AtomicInteger counter = new AtomicInteger();

        WikiUtils.newInstance()
                 .connectToWikiDataAndWikipedia()
                 .usingLocalCache()
                 .searchFor(SPARQLFilters.INSTANCE_OF_HOSPITAL)
                 .stream()
                 .skip(0)
                 //                 .limit(100)
                 .forEach(item ->
                 {
                     System.out.println("----------- " + counter.incrementAndGet() + " -------------");
                     item.getTitle()
                         .ifPresent(t ->
                         {
                             String homepage = item.getHomePage()
                                                   .orElse(null);
                             System.out.println(t + " : " + homepage);
                             cache.put(t, homepage);
                         });
                 });

    }

    @Test
    @Ignore
    public void testDrugs() throws Exception
    {
        Cache cache = NitriteRepositoryUtils.newLocalCache("drug");

        AtomicInteger counter = new AtomicInteger();

        WikiUtils.newInstance()
                 .connectToWikiDataAndWikipedia()
                 .usingLocalCache()
                 .searchFor(SPARQLFilters.INSTANCE_OF_DRUG)
                 .stream()
                 .skip(0)
                 //                 .limit(100)
                 .forEach(item ->
                 {
                     System.out.println("----------- " + counter.incrementAndGet() + " -------------");
                     item.getTitle()
                         .ifPresent(t ->
                         {
                             String description = item.getDescription()
                                                      .orElse(null);
                             System.out.println(t + " : " + description);
                             cache.put(t, description);
                         });
                 });

    }

    @Test
    //    @Ignore
    public void testWikiTexts() throws Exception
    {
        Cache cache = NitriteRepositoryUtils.newLocalCache("texts");

        AtomicInteger counter = new AtomicInteger();

        WikiUtils.newInstance()
                 .connectToWikiDataAndWikipedia()
                 .usingLocalCache()
                 .searchFor(SPARQLFilters.INSTANCE_OF_HUMAN)
                 .stream()
                 .skip(1000)
                 .limit(50000)
                 .forEach(item ->
                 {
                     System.out.println("----------- " + counter.incrementAndGet() + " -------------");
                     item.getTitle()
                         .ifPresent(title ->
                         {
                             System.out.println(title);
                             item.resolveText()
                                 .ifPresent(text ->
                                 {
                                     cache.put(title, text);
                                     System.out.println(text);
                                 });
                         });
                 });

    }

}

package org.omnaest.wiki.rest;

import org.junit.Test;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLFilters;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLResults;

public class WikiRESTUtilsTest
{

    @Test
    public void testNewInstance() throws Exception
    {
        SPARQLResults result = WikiRESTUtils.newInstance()
                                            .usingLocalCache()
                                            .fetchStream(SPARQLFilters.INSTANCE_OF_HOSPITAL, SPARQLFilters.COUNTRY_GERMANY);
        result.getBindings()
              .forEach(binding ->
              {
                  System.out.println(binding);
              });
    }

}

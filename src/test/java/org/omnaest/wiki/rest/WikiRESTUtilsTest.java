package org.omnaest.wiki.rest;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLFilters;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLResults;

public class WikiRESTUtilsTest
{

    @Test
    @Ignore
    public void testNewInstance() throws Exception
    {
        SPARQLFilters filter = SPARQLFilters.INSTANCE_OF_UNIVERSITY_HOSPITAL;
        SPARQLResults result = WikiRESTUtils.newInstance()
                                            .usingLocalCache()
                                            .fetchStream(filter);

        System.out.println(filter.get());
        result.getBindings()
              .forEach(binding ->
              {
                  System.out.println(binding);
              });
    }

}

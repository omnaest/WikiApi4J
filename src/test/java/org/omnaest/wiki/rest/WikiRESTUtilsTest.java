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

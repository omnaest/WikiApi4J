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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.omnaest.utils.ListUtils;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.rest.client.RestClient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WikiRESTUtils
{
    public static class Head
    {
        @JsonProperty("vars")
        private List<String> variables;

        public List<String> getVariables()
        {
            return this.variables;
        }

        @Override
        public String toString()
        {
            return "Head [variables=" + this.variables + "]";
        }

    }

    public static class EntityObject
    {
        @JsonProperty
        private String type;

        @JsonProperty
        private String value;

        public String getType()
        {
            return this.type;
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "EntityObject [type=" + this.type + ", value=" + this.value + "]";
        }

    }

    public static class Results
    {
        @JsonProperty
        private List<Map<String, EntityObject>> bindings;

        public List<Map<String, EntityObject>> getBindings()
        {
            return this.bindings;
        }

        @Override
        public String toString()
        {
            return "Results [bindings=" + this.bindings + "]";
        }

    }

    public static class Binding
    {
        private Map<String, EntityObject> variableToEntityObject;

        @JsonCreator
        public Binding(Map<String, EntityObject> variableToEntityObject)
        {
            super();
            this.variableToEntityObject = variableToEntityObject;
        }

        @JsonIgnore
        public EntityObject get(String variable)
        {
            return this.variableToEntityObject.get(variable);
        }

        @Override
        public String toString()
        {
            return "Binding [variableToEntityObject=" + this.variableToEntityObject + "]";
        }

        @JsonIgnore
        public Optional<EntityObject> getFirstValue()
        {
            return this.variableToEntityObject.values()
                                              .stream()
                                              .findFirst();
        }

    }

    public static class SPARQLResult
    {
        @JsonProperty
        private Head head;

        @JsonProperty
        private Results results;

        @JsonIgnore
        public List<String> getVariables()
        {
            return this.head.getVariables();
        }

        @JsonIgnore
        public List<Binding> getBindings()
        {
            return this.results.getBindings()
                               .stream()
                               .map(map -> new Binding(map))
                               .collect(Collectors.toList());
        }

        @Override
        public String toString()
        {
            return "SPARQLResult [head=" + this.head + ", results=" + this.results + "]";
        }

    }

    public static interface SPARQLResults
    {
        public Stream<Binding> getBindings();
    }

    public static class SPARQLFilter
    {
        private String name;
        private String value;

        public SPARQLFilter(String name, String value)
        {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName()
        {
            return this.name;
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "SPARQLFilter [name=" + this.name + ", value=" + this.value + "]";
        }

    }

    public static interface SPARQLFilterValueProvider extends Supplier<String>
    {
        public static SPARQLFilterValueProvider of(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider objects)
        {
            return () -> property.get()
                                 .stream()
                                 .map(value -> "wdt:" + value)
                                 .collect(Collectors.joining("/"))
                    + " wd:" + objects.get();
        }
    }

    public static interface SPARQLPropertyValueProvider extends Supplier<List<String>>
    {
    }

    public static interface SPARQLObjectValueProvider extends Supplier<String>
    {
    }

    public static enum SPARQLProperties implements SPARQLPropertyValueProvider
    {
        INSTANCE_OF("P31", "P279*"),
        EXACT_INSTANCE_OF("P31"),
        FIELD_OF_WORK("P101"),
        COUNTRY("P17"),
        CITY("P131"),
        CONTINENT("P30"),
        COORDINATE("P625"),
        OFFICIAL_WEBSITE("P856"),
        FIELD_OF_THIS_OCCUPATION("P425"),
        SAID_TO_BE_SAME_AS("P460"),
        SUBCLASS_OF("P279");

        private String   value;
        private String[] additionalValues;

        private SPARQLProperties(String value, String... additionalValues)
        {
            this.value = value;
            this.additionalValues = additionalValues;
        }

        @Override
        public List<String> get()
        {
            return ListUtils.addToNew(ListUtils.toList(this.additionalValues), 0, this.value);
        }
    }

    public static enum SPARQLObjects implements SPARQLObjectValueProvider
    {
        HUMAN("Q5"),
        HOSPITAL("Q16917"),
        UNIVERSITY_HOSPITAL("Q1059324"),
        UNIVERSITY("Q3918"),
        RESEARCH_INSTITUTE("Q31855"),
        MEDICAL_RESEARCH_INSTITUTE("Q66737615"),
        MEDICAL_RESEARCH("Q2752427"),
        EUROPE("Q46"),
        GERMANY("Q183"),
        DRUG("Q8386"),
        MEDICATION("Q12140");

        private String value;

        private SPARQLObjects(String value)
        {
            this.value = value;
        }

        @Override
        public String get()
        {
            return this.value;
        }
    }

    public static enum SPARQLFilters implements SPARQLFilterValueProvider
    {
        INSTANCE_OF_HUMAN(SPARQLProperties.INSTANCE_OF, SPARQLObjects.HUMAN),
        INSTANCE_OF_HOSPITAL(SPARQLProperties.INSTANCE_OF, SPARQLObjects.HOSPITAL),
        INSTANCE_OF_DRUG(SPARQLProperties.INSTANCE_OF, SPARQLObjects.DRUG),
        INSTANCE_OF_MEDICATION(SPARQLProperties.INSTANCE_OF, SPARQLObjects.MEDICATION),
        INSTANCE_OF_UNIVERSITY_HOSPITAL(SPARQLProperties.INSTANCE_OF, SPARQLObjects.UNIVERSITY_HOSPITAL),
        INSTANCE_OF_UNIVERSITY(SPARQLProperties.INSTANCE_OF, SPARQLObjects.UNIVERSITY),
        INSTANCE_OF_RESEARCH_INSTITUTE(SPARQLProperties.INSTANCE_OF, SPARQLObjects.RESEARCH_INSTITUTE),
        INSTANCE_OF_MEDICAL_RESEARCH_INSTITUTE(SPARQLProperties.INSTANCE_OF, SPARQLObjects.MEDICAL_RESEARCH_INSTITUTE),
        FIELD_OF_WORK_IS_MEDICAL_RESEARCH(SPARQLProperties.FIELD_OF_WORK, SPARQLObjects.MEDICAL_RESEARCH),
        COUNTRY_GERMANY(SPARQLProperties.COUNTRY, SPARQLObjects.GERMANY);

        private String filterValue;

        private SPARQLFilters(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider objects)
        {
            this(SPARQLFilterValueProvider.of(property, objects)
                                          .get());
        }

        private SPARQLFilters(String filterValue)
        {
            this.filterValue = filterValue;
        }

        @Override
        public String get()
        {
            return this.filterValue;
        }

    }

    public static class SPARQLFilterExpression
    {
        private List<SPARQLFilter> filters = new ArrayList<>();
        private int                counter = 0;

        public SPARQLFilterExpression addFilter(SPARQLFilterValueProvider filter)
        {
            return this.addFilter(filter.get());
        }

        public SPARQLFilterExpression addFilter(String... values)
        {
            return this.addFilter(Arrays.asList(values));
        }

        public SPARQLFilterExpression addFilter(List<String> values)
        {
            String name = "var" + ++this.counter;
            values.forEach(value -> this.addFilter(name, value));
            return this;
        }

        public SPARQLFilterExpression addFilter(String value)
        {
            return this.addFilter(new String[] { value });
        }

        public SPARQLFilterExpression addFilter(String name, String value)
        {
            this.filters.add(new SPARQLFilter(name, value));
            return this;
        }

        public SPARQLFilterExpression addFilters(SPARQLFilterValueProvider... filters)
        {
            this.addFilter(Arrays.asList(filters)
                                 .stream()
                                 .map(filter -> filter.get())
                                 .collect(Collectors.toList()));
            return this;
        }

        protected List<SPARQLFilter> getFilters()
        {
            return this.filters;
        }

    }

    public static class SPARQLExpression
    {
        private SPARQLFilterExpression filterExpression;
        private int                    limit  = Integer.MAX_VALUE;
        private int                    offset = 0;

        public SPARQLExpression(SPARQLFilterExpression filterExpression)
        {
            super();
            this.filterExpression = filterExpression;
        }

        public SPARQLExpression()
        {
            super();
            this.filterExpression = new SPARQLFilterExpression();
        }

        public SPARQLExpression addFilter(String value)
        {
            this.filterExpression.addFilter(value);
            return this;
        }

        public SPARQLExpression addFilter(String name, String value)
        {
            this.filterExpression.addFilter(name, value);
            return this;
        }

        public SPARQLExpression setFilterExpression(SPARQLFilterExpression expression)
        {
            this.filterExpression = expression;
            return this;
        }

        public String asString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append("SELECT ?" + this.filterExpression.getFilters()
                                                        .stream()
                                                        .map(filter -> filter.getName())
                                                        .distinct()
                                                        .collect(Collectors.joining(" ?"))
                    + " ");
            sb.append("WHERE {");
            this.filterExpression.getFilters()
                                 .forEach(filter ->
                                 {
                                     String name = filter.getName();
                                     String value = filter.getValue();
                                     sb.append("?" + name + " " + value + ".");
                                     sb.append("\n");
                                 });
            sb.append(" }");
            sb.append(" LIMIT " + this.limit);
            sb.append(" OFFSET " + this.offset);

            return sb.toString();
        }

        @Override
        public String toString()
        {
            return this.asString();
        }

        public SPARQLExpression setLimit(int limit)
        {
            this.limit = limit;
            return this;
        }

        public SPARQLExpression setOffset(int offset)
        {
            this.offset = offset;
            return this;
        }

    }

    public static interface WikiRESTAccessor
    {
        public static final String DEFAULT_WIKIDATA_URL = "https://query.wikidata.org";

        public WikiRESTAccessor usingLocalCache();

        public SPARQLResult fetch(Consumer<SPARQLExpression> expressionConsumer);

        public SPARQLResults fetchStream(Consumer<SPARQLFilterExpression> expressionConsumer);

        public SPARQLResults fetchStream(SPARQLFilterValueProvider filter);

        public SPARQLResults fetchStream(SPARQLFilterValueProvider... filters);

        public SPARQLResults fetchStream(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider object);

        /**
         * Allows to specify the wiki url. Default is {@value #DEFAULT_WIKIDATA_URL}
         * 
         * @param url
         * @return
         */
        public WikiRESTAccessor connectTo(String url);

    }

    private static class WikiRESTAccessorImpl implements WikiRESTAccessor
    {
        private RestClient restClient = RestClient.newJSONRestClient();
        private String     url        = DEFAULT_WIKIDATA_URL;

        @Override
        public SPARQLResults fetchStream(Consumer<SPARQLFilterExpression> expressionConsumer)
        {
            SPARQLFilterExpression expression = new SPARQLFilterExpression();
            expressionConsumer.accept(expression);
            return new SPARQLResults()
            {
                @Override
                public Stream<Binding> getBindings()
                {
                    int pageSize = 1000;
                    AtomicBoolean lastResult = new AtomicBoolean(false);
                    return StreamUtils.generate()
                                      .intStream()
                                      .unlimitedWithTerminationPredicate(ii -> lastResult.get())
                                      .mapToObj(ii -> WikiRESTAccessorImpl.this.fetch(pagedExpression -> pagedExpression.setFilterExpression(expression)
                                                                                                                        .setOffset(ii * pageSize)
                                                                                                                        .setLimit(pageSize)))
                                      .peek(result -> lastResult.set(result == null || result.getBindings() == null || result.getBindings()
                                                                                                                             .isEmpty()))
                                      .filter(PredicateUtils.notNull())
                                      .flatMap(result -> result.getBindings()
                                                               .stream());
                }
            };
        }

        @Override
        public SPARQLResult fetch(Consumer<SPARQLExpression> expressionConsumer)
        {
            SPARQLExpression expression = new SPARQLExpression();
            expressionConsumer.accept(expression);
            String expressionBody = expression.asString();
            return this.restClient.request()
                                  .toUrl(builder -> builder.setBaseUrl(this.url)
                                                           .addPathToken("sparql")
                                                           .addQueryParameter("query", expressionBody))
                                  .get(SPARQLResult.class);
        }

        @Override
        public WikiRESTAccessor usingLocalCache()
        {
            this.restClient = this.restClient.withLocalCache("wiki-rest-calls-" + this.determineFileNameFromUrl(this.url));
            return this;
        }

        private String determineFileNameFromUrl(String url)
        {
            return StringUtils.removeEnd(RegExUtils.replaceAll(url, "[^a-zA-Z]+", "-"), "-");
        }

        @Override
        public SPARQLResults fetchStream(SPARQLFilterValueProvider filter)
        {
            return this.fetchStream(new SPARQLFilterValueProvider[] { filter });
        }

        @Override
        public SPARQLResults fetchStream(SPARQLFilterValueProvider... filters)
        {
            return this.fetchStream(expression -> expression.addFilters(filters));
        }

        @Override
        public SPARQLResults fetchStream(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider objects)
        {
            return this.fetchStream(expression -> expression.addFilter(SPARQLFilterValueProvider.of(property, objects)));
        }

        @Override
        public WikiRESTAccessor connectTo(String url)
        {
            this.url = url;
            return this;
        }
    }

    public static WikiRESTAccessor newInstance()
    {
        return new WikiRESTAccessorImpl();
    }
}

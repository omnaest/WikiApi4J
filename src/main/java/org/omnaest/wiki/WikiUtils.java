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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.omnaest.utils.CacheUtils;
import org.omnaest.utils.CollectorUtils;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.cache.Cache;
import org.omnaest.utils.cache.Cache.EvictionStrategy;
import org.omnaest.utils.element.bi.BiElement;
import org.omnaest.utils.element.cached.CachedElement;
import org.omnaest.utils.html.HtmlUtils;
import org.omnaest.utils.html.HtmlUtils.HtmlDocumentLoader;
import org.omnaest.utils.rest.client.RestHelper.RESTAccessExeption;
import org.omnaest.utils.stream.Streamable;
import org.omnaest.utils.supplier.EnumSupplier;
import org.omnaest.wiki.rest.WikiRESTUtils;
import org.omnaest.wiki.rest.WikiRESTUtils.Binding;
import org.omnaest.wiki.rest.WikiRESTUtils.EntityObject;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLFilterValueProvider;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLObjectValueProvider;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLProperties;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLPropertyValueProvider;
import org.omnaest.wiki.rest.WikiRESTUtils.SPARQLResults;
import org.omnaest.wiki.rest.WikiRESTUtils.WikiRESTAccessor;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocument;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.wikibaseapi.WbSearchEntitiesResult;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;

public class WikiUtils
{

    private static final String DEFAULT_WIKIPEDIA_EN_URL = "https://en.wikipedia.org";

    public static interface WikiAccessorLoader
    {
        public WikiAccessor connectTo(String wikiDataUrl, String wikiPediaUrl);

        public WikiAccessor connectToWikiDataAndWikipedia();
    }

    public static interface WikiAccessor
    {
        public WikiAccessor usingLocalCache();

        public SearchResult searchFor(String query);

        public SearchResult searchFor(SPARQLFilterValueProvider filter);

        public SearchResult searchFor(SPARQLFilterValueProvider... filters);

        public SearchResult searchFor(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider object);

        Optional<Item> findByEntityId(String entityId);

    }

    public static interface SearchResult extends Streamable<Item>
    {
    }

    public static interface Item
    {
        public Optional<String> resolveText();

        public Optional<String> getTitle();

        public Optional<String> getTitle(LanguageProvider language);

        public Optional<String> getDescription();

        public Optional<String> getDescription(LanguageProvider language);

        public Optional<String> getHomePage();

        public Optional<String> getCountry();

        public Optional<String> getCity();

        public String getEntityId();

        public Optional<StatementResult> getStatement(SPARQLProperties sparqlProperties);

        public Stream<Item> getInstanceOf();

        public Stream<Item> getExactInstanceOf();

        /**
         * <a href="https://www.wikidata.org/wiki/Property:P460">Wiki</a>
         * 
         * @return
         */
        public Stream<Item> getSaidToBeSameAs();

        /**
         * <a href="https://www.wikidata.org/wiki/Property:P279">Wiki</a>
         * 
         * @return
         */
        public Stream<Item> getSubclassOf();

        /**
         * <a href="https://www.wikidata.org/wiki/Property:P425">Wiki</a>
         * 
         * @return
         */
        public Stream<Item> getFieldOfOccupation();

        public static interface StatementResult
        {
            public Item asItem();

            public String asString();
        }

    }

    public static interface LanguageProvider extends EnumSupplier
    {
        public default String getKey()
        {
            return this.name()
                       .toLowerCase();
        }
    }

    public static enum Language implements LanguageProvider
    {
        EN, DE
    }

    public static WikiAccessorLoader newInstance()
    {
        return new WikiAccessorLoader()
        {
            @Override
            public WikiAccessor connectTo(String wikiDataUrl, String wikiPediaUrl)
            {
                return new WikiAccessorImpl(wikiDataUrl, wikiPediaUrl);
            }

            @Override
            public WikiAccessor connectToWikiDataAndWikipedia()
            {
                return this.connectTo(WikiRESTAccessor.DEFAULT_WIKIDATA_URL, DEFAULT_WIKIPEDIA_EN_URL);
            }
        };
    }

    private static class WikiAccessorImpl implements WikiAccessor
    {
        private static final Language DEFAULT_LANGUAGE = Language.EN;

        private String wikiDataUrl;
        private String wikiPediaUrl;

        private WikiRESTAccessor    wikiAccessor;
        private ItemDocumentFetcher fetcher = this.initFetcher()
                                                  .withCache();
        private HtmlDocumentLoader  htmlDocumentLoader;

        private WikiAccessorImpl(String wikiDataUrl, String wikiPediaUrl)
        {
            this.wikiDataUrl = wikiDataUrl;
            this.wikiPediaUrl = wikiPediaUrl;
            this.wikiAccessor = WikiRESTUtils.newInstance()
                                             .connectTo(wikiDataUrl);
            this.htmlDocumentLoader = HtmlUtils.load();
        }

        private ItemDocumentFetcher initFetcher()
        {
            WikibaseDataFetcher fetcher = WikibaseDataFetcher.getWikidataDataFetcher();
            //            fetcher.getFilter()
            //                   .setSiteLinkFilter(Collections.singleton("enwiki"));
            //            fetcher.getFilter()
            //                   .setLanguageFilter(Collections.singleton(DEFAULT_LANGUAGE_KEY));
            return new ItemDocumentFetcher()
            {
                @Override
                public Map<String, ItemDocument> apply(List<String> entityIds)
                {
                    try
                    {
                        Map<String, EntityDocument> entityIdsToEntityDocument = fetcher.getEntityDocuments(entityIds);
                        return entityIdsToEntityDocument.entrySet()
                                                        .stream()
                                                        .filter(entry -> entry.getValue() instanceof ItemDocument)
                                                        .map(entry -> BiElement.of(entry.getKey(), (ItemDocument) entry.getValue()))
                                                        .collect(CollectorUtils.toMapByBiElement());
                    }
                    catch (Exception e)
                    {
                        return Collections.emptyMap();
                    }
                }
            };
        }

        @Override
        public SearchResult searchFor(String query)
        {
            try
            {
                Function<String, List<String>> queryFunction = CacheUtils.newLocalJsonFolderCache("wikiSearch")
                                                                         .<List<String>>asUnaryCache(List.class)
                                                                         .asSuppliedFunction(iQuery ->
                                                                         {
                                                                             try
                                                                             {
                                                                                 return WikibaseDataFetcher.getWikidataDataFetcher()
                                                                                                           .searchEntities(iQuery)
                                                                                                           .stream()
                                                                                                           .map(WbSearchEntitiesResult::getEntityId)
                                                                                                           .filter(PredicateUtils.notBlank())
                                                                                                           .collect(Collectors.toList());
                                                                             }
                                                                             catch (Exception e)
                                                                             {
                                                                                 throw new IllegalStateException(e);
                                                                             }
                                                                         });
                List<String> entityIds = queryFunction.apply(query);
                return this.newSearchResult(StreamUtils.framedNonNullAsList(25, entityIds.stream()));
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SearchResult searchFor(SPARQLFilterValueProvider filter)
        {
            return this.searchFor(new SPARQLFilterValueProvider[] { filter });
        }

        @Override
        public SearchResult searchFor(SPARQLFilterValueProvider... filters)
        {
            SPARQLResults results = this.wikiAccessor.fetchStream(filters);
            Stream<String> entityIds = results.getBindings()
                                              .map(Binding::getFirstValue)
                                              .filter(Optional::isPresent)
                                              .map(Optional::get)
                                              .map(EntityObject::getValue)
                                              .map(this::determineEntityIdFromUrl)
                                              .filter(PredicateUtils.notBlank());

            Stream<List<String>> entityIdBlocks = StreamUtils.framedAsList(20, entityIds);

            return this.newSearchResult(entityIdBlocks);
        }

        private SearchResult newSearchResult(Stream<List<String>> entityIdBlocks)
        {
            return new SearchResult()
            {
                @Override
                public Stream<Item> stream()
                {
                    return entityIdBlocks.flatMap(entityIds -> this.newItemBlock(entityIds));
                }

                private Stream<Item> newItemBlock(List<String> entityIds)
                {
                    ItemDocumentResolver itemDocumentResolver = new ItemDocumentResolver(entityIds, WikiAccessorImpl.this.fetcher);
                    return entityIds.stream()
                                    .map(entityId -> this.newItem(entityId, itemDocumentResolver));
                }

                private Item newItem(String entityId, Function<String, ItemDocument> itemDocumentResolver)
                {
                    return new ItemImpl(entityId, itemDocumentResolver);
                }

            };
        }

        protected static class ItemDocumentResolver implements Function<String, ItemDocument>
        {
            private CachedElement<Map<String, ItemDocument>> entityIdToItemDocument;
            private ItemDocumentFetcher                      fetcher;

            public ItemDocumentResolver(List<String> entityIds, ItemDocumentFetcher fetcher)
            {
                super();
                this.fetcher = fetcher;
                this.entityIdToItemDocument = CachedElement.of(() -> fetcher.apply(entityIds));
            }

            @Override
            public ItemDocument apply(String entityId)
            {
                return Optional.ofNullable(this.entityIdToItemDocument.get()
                                                                      .get(entityId))
                               .orElseGet(() -> this.fetcher.apply(Arrays.asList(entityId))
                                                            .get(entityId));
            }

        }

        private String determineEntityIdFromUrl(String entityUrl)
        {
            return StringUtils.substringAfterLast(entityUrl, "/");
        }

        private String resolveWikiText(String title)
        {
            return this.htmlDocumentLoader.fromUrl(this.wikiPediaUrl + "/wiki/" + this.determineWikiEncodedTitle(title))
                                          .findById("bodyContent")
                                          .map(element -> element.findByTag("p"))
                                          .get()
                                          .map(element -> element.asText())
                                          .collect(Collectors.joining("\n"));
        }

        private String determineWikiEncodedTitle(String title)
        {
            return RegExUtils.replaceAll(title, "[^a-zA-Z]", "_");
        }

        @Override
        public SearchResult searchFor(SPARQLPropertyValueProvider property, SPARQLObjectValueProvider object)
        {
            return this.searchFor(SPARQLFilterValueProvider.of(property, object));
        }

        @Override
        public Optional<Item> findByEntityId(String entityId)
        {
            ItemDocumentResolver itemDocumentResolver = new ItemDocumentResolver(Arrays.asList(entityId), this.fetcher);
            if (itemDocumentResolver.apply(entityId) != null)
            {
                return Optional.of(this.newItem(entityId, itemDocumentResolver));
            }
            else
            {
                return Optional.empty();
            }
        }

        private Item newItem(String entityId, Function<String, ItemDocument> itemDocumentResolver)
        {
            return new ItemImpl(entityId, itemDocumentResolver);
        }

        @Override
        public WikiAccessor usingLocalCache()
        {
            this.wikiAccessor = this.wikiAccessor.usingLocalCache();
            this.htmlDocumentLoader = this.htmlDocumentLoader.usingLocalCache();
            return this;
        }

        public static interface ItemDocumentFetcher extends Function<List<String>, Map<String, ItemDocument>>
        {
            public default ItemDocumentFetcher withCache()
            {
                return new ItemDocumentFetcherCacheImpl(this);
            }
        }

        public static class ItemDocumentFetcherCacheImpl implements ItemDocumentFetcher
        {
            private ItemDocumentFetcher parent;
            private Cache               cache = CacheUtils.newConcurrentInMemoryCache()
                                                          .withCapacityLimit(10000, EvictionStrategy.RANDOM)
                                                          .withEvictionRatio(0.8);

            public ItemDocumentFetcherCacheImpl(ItemDocumentFetcher parent)
            {
                super();
                this.parent = parent;
            }

            @Override
            public Map<String, ItemDocument> apply(List<String> entityIds)
            {
                return entityIds.stream()
                                .distinct()
                                .map(entityId ->
                                {
                                    ItemDocument document = Optional.ofNullable(this.cache.get(entityId, ItemDocument.class))
                                                                    .orElseGet(() ->
                                                                    {
                                                                        Map<String, ItemDocument> result = this.parent.apply(entityIds);
                                                                        this.cache.putAll(result);
                                                                        return result.get(entityId);
                                                                    });
                                    return BiElement.of(entityId, document);
                                })
                                .filter(bi -> !bi.hasAnyNullValue())
                                .collect(CollectorUtils.toMapByBiElement());
            }
        }

        private class ItemImpl implements Item
        {
            private final String                   entityId;
            private Function<String, ItemDocument> itemDocumentResolver;

            private ItemImpl(String entityId, Function<String, ItemDocument> itemDocumentResolver)
            {
                this.entityId = entityId;
                this.itemDocumentResolver = itemDocumentResolver;
            }

            @Override
            public Optional<String> getTitle()
            {
                return this.getTitle(DEFAULT_LANGUAGE);
            }

            @Override
            public Optional<String> getTitle(LanguageProvider language)
            {

                return Optional.ofNullable(this.itemDocumentResolver.apply(this.entityId))
                               .map(ItemDocument::getLabels)
                               .map(labels -> labels.get(language.getKey()))
                               .map(MonolingualTextValue::getText);
            }

            @Override
            public Optional<String> resolveText()
            {
                try
                {
                    String title = Optional.ofNullable(this.itemDocumentResolver.apply(this.entityId))
                                           .map(document -> document.getSiteLinks())
                                           .map(links -> links.get("enwiki"))
                                           .map(link -> link.getPageTitle())
                                           .orElseGet(() -> this.getTitle()
                                                                .orElse(null));
                    //
                    //                    String url = "https://www.wikidata.org/w/api.php?action=wbgetentities&sites=" + siteKey + "&titles=" + pageTitle + "&languages="
                    //                            + language.getKey();

                    return Optional.ofNullable(title)
                                   .map(t -> WikiAccessorImpl.this.resolveWikiText(t));
                }
                catch (RESTAccessExeption e)
                {
                    if (e.getStatusCode() == 404)
                    {
                        return Optional.empty();
                    }
                    else
                    {
                        throw e;
                    }
                }
            }

            @Override
            public Optional<String> getDescription()
            {
                return this.getDescription(DEFAULT_LANGUAGE);
            }

            @Override
            public Optional<String> getDescription(LanguageProvider language)
            {
                return Optional.ofNullable(this.itemDocumentResolver.apply(this.entityId))
                               .map(ItemDocument::getDescriptions)
                               .map(descriptions -> descriptions.get(language.getKey()))
                               .map(MonolingualTextValue::getText);
            }

            @Override
            public Optional<String> getHomePage()
            {
                return this.getStatement(SPARQLProperties.OFFICIAL_WEBSITE)
                           .map(StatementResult::asString);
            }

            @Override
            public Optional<String> getCountry()
            {
                return this.getStatement(SPARQLProperties.COUNTRY)
                           .map(StatementResult::asItem)
                           .map(item -> item.getTitle()
                                            .orElse(null));
            }

            @Override
            public Optional<String> getCity()
            {
                return this.getStatement(SPARQLProperties.CITY)
                           .map(StatementResult::asItem)
                           .map(item -> item.getTitle()
                                            .orElse(null));
            }

            @Override
            public Stream<Item> getInstanceOf()
            {
                return this.getStatements(SPARQLProperties.INSTANCE_OF)
                           .map(StatementResult::asItem);
            }

            @Override
            public Stream<Item> getExactInstanceOf()
            {
                return this.getStatements(SPARQLProperties.EXACT_INSTANCE_OF)
                           .map(StatementResult::asItem);
            }

            @Override
            public Stream<Item> getFieldOfOccupation()
            {
                return this.getStatements(SPARQLProperties.FIELD_OF_THIS_OCCUPATION)
                           .map(StatementResult::asItem);
            }

            @Override
            public Stream<Item> getSaidToBeSameAs()
            {
                return this.getStatements(SPARQLProperties.SAID_TO_BE_SAME_AS)
                           .map(StatementResult::asItem);
            }

            @Override
            public Stream<Item> getSubclassOf()
            {
                return this.getStatements(SPARQLProperties.SUBCLASS_OF)
                           .map(StatementResult::asItem);
            }

            @Override
            public Optional<StatementResult> getStatement(SPARQLProperties property)
            {
                return this.getStatements(property)
                           .findFirst();
            }

            public Stream<StatementResult> getStatements(SPARQLProperties property)
            {
                return Optional.ofNullable(property)
                               .map(SPARQLProperties::get)
                               .map(List::stream)
                               .orElse(Stream.empty())
                               .flatMap(propertyId ->
                               {
                                   ItemDocument itemDocument = this.itemDocumentResolver.apply(this.entityId);
                                   return Optional.ofNullable(itemDocument)
                                                  .map(document -> document.findStatementGroup(propertyId))
                                                  .map(StatementGroup::getStatements)
                                                  .map(List::stream)
                                                  .orElse(Stream.empty());
                               })
                               .filter(PredicateUtils.notNull())
                               .map(iStatement -> new StatementResult()
                               {
                                   @Override
                                   public String asString()
                                   {
                                       return Optional.ofNullable(iStatement.getValue())
                                                      .filter(value -> value instanceof StringValue)
                                                      .map(value -> ((StringValue) value))
                                                      .map(StringValue::getString)
                                                      .orElse(null);
                                   }

                                   @Override
                                   public Item asItem()
                                   {
                                       return Optional.ofNullable(iStatement.getValue())
                                                      .filter(value -> value instanceof EntityIdValue)
                                                      .map(value -> ((EntityIdValue) value))
                                                      .map(EntityIdValue::getId)
                                                      .map(entityId -> new ItemImpl(entityId, ItemImpl.this.itemDocumentResolver))
                                                      .orElse(null);
                                   }
                               });
            }

            @Override
            public String getEntityId()
            {
                return this.entityId;
            }
        }
    }
}

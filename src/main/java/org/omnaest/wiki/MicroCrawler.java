package org.omnaest.wiki;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.omnaest.utils.MatcherUtils;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.SetUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.URLUtils;
import org.omnaest.utils.html.HtmlUtils;
import org.omnaest.utils.html.HtmlUtils.HtmlAnker;
import org.omnaest.utils.html.HtmlUtils.HtmlDocument;
import org.omnaest.utils.html.HtmlUtils.HtmlElement;

public class MicroCrawler
{
    private int     maxNumberOfRequests = 100;
    private Pattern matcher;

    public static interface RegexSupplier extends Supplier<String>
    {
    }

    public static enum Matchers implements RegexSupplier
    {
        EMAIL("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])");

        private String regex;

        private Matchers(String regex)
        {
            this.regex = regex;
        }

        @Override
        public String get()
        {
            return this.regex;
        }
    }

    private MicroCrawler()
    {
        super();
    }

    public MicroCrawler withMaxNumberOfRequests(int maxNumberOfRequests)
    {
        this.maxNumberOfRequests = maxNumberOfRequests;
        return this;
    }

    /**
     * @see Matchers
     * @param matchers
     * @return
     */
    public MicroCrawler addMatcher(RegexSupplier matchers)
    {
        return this.addMatcher(matchers.get());
    }

    public MicroCrawler addMatcher(String regex)
    {
        return this.addMatcher(Pattern.compile(regex));
    }

    public MicroCrawler addMatcher(Pattern matcher)
    {
        this.matcher = matcher;
        return this;
    }

    public static class Match
    {
        private String       value;
        private List<String> contexts;

        public Match(String value, List<String> contexts)
        {
            super();
            this.value = value;
            this.contexts = contexts;
        }

        public String getValue()
        {
            return this.value;
        }

        public List<String> getContexts()
        {
            return this.contexts;
        }

        @Override
        public String toString()
        {
            return "Match [value=" + this.value + ", contexts=" + this.contexts + "]";
        }

    }

    public static interface AnalysisResult
    {

        public Stream<Match> getMatches();

    }

    private static class MatchesCollector
    {
        private Map<String, List<String>> valueToContexts = new HashMap<>();

        public void putAll(Map<HtmlElement, Set<String>> matches)
        {
            matches.forEach((key, values) ->
            {
                values.forEach(value ->
                {
                    this.valueToContexts.computeIfAbsent(value, v -> new ArrayList<>())
                                        .addAll(StreamUtils.builder()
                                                           .add(key)
                                                           .addAll(key.getParents()
                                                                      .limit(3))
                                                           .build()
                                                           .map(HtmlElement::asText)
                                                           .collect(Collectors.toList()));
                });
            });
        }

        public Stream<Match> getMatches()
        {
            return this.valueToContexts.entrySet()
                                       .stream()
                                       .map(entry -> new Match(entry.getKey(), entry.getValue()));
        }

    }

    public AnalysisResult analyze(String url)
    {
        Set<String> urls = Collections.synchronizedSet(new LinkedHashSet<>(Arrays.asList(url)));

        MatchesCollector collector = new MatchesCollector();

        IntStream.range(0, this.maxNumberOfRequests)
                 .mapToObj(ii -> urls.stream()
                                     .skip(ii)
                                     .findFirst())
                 .filter(Optional::isPresent)
                 .map(Optional::get)
                 .peek(System.out::println)
                 .map(currentUrl -> this.fetchAndAnalyzePage(currentUrl, collector))
                 .peek(System.out::println)
                 .forEach(urls::addAll);

        return new AnalysisResult()
        {
            @Override
            public Stream<Match> getMatches()
            {
                return collector.getMatches();
            }
        };
    }

    private Set<String> fetchAndAnalyzePage(String currentUrl, MatchesCollector collector)
    {
        try
        {
            HtmlDocument htmlDocument = HtmlUtils.load()
                                                 .fromUrl(currentUrl);

            this.collectMatches(collector, htmlDocument);

            return htmlDocument.findByTag("a")
                               .map(HtmlElement::asAnker)
                               .map(optional -> optional.orElse(null))
                               .map(HtmlAnker::getHref)
                               .filter(PredicateUtils.notBlank())
                               .distinct()
                               .map(relativeUrl -> URLUtils.from(currentUrl)
                                                           .navigateTo(relativeUrl)
                                                           .map(u -> u.removeTrailingAnker())
                                                           .map(e -> e.get())
                                                           .orElse(null))
                               .collect(Collectors.toSet());
        }
        catch (Exception e)
        {
            return SetUtils.empty();
        }
    }

    private void collectMatches(MatchesCollector collector, HtmlDocument htmlDocument)
    {
        if (this.matcher != null)
        {
            Map<HtmlElement, Set<String>> matches = new HashMap<>();

            htmlDocument.visit((element, parents) ->
            {
                String input = element.asText();

                AtomicBoolean hasMatchResults = new AtomicBoolean(false);
                MatcherUtils.matcher()
                            .of(this.matcher)
                            .findIn(input)
                            .ifPresent(result ->
                            {
                                Set<String> values = result.map(match -> match.getMatchRegion())
                                                           .collect(Collectors.toSet());
                                if (!values.isEmpty())
                                {
                                    matches.keySet()
                                           .removeAll(parents.getElements());
                                    matches.put(element, values);
                                    hasMatchResults.set(true);
                                }
                            });

                return hasMatchResults.get();
            });

            collector.putAll(matches);
        }
    }

    public static MicroCrawler newInstance()
    {
        return new MicroCrawler();
    }
}

# Template Matcher

Load templates from disc and use apache StrSubstitutor to match params and template attributes. If
configured to auto purge, a dedicated daemon is created to evict LRU loaded template after elapsed
configured idle time.

Usage:
----

``` java

        Map<String,String> params = new HashMap<>();
        
        TemplateMatcher matcher = TemplateMatcher.single(true,60,TimeUnit.MINUTES);
        matcher.template("<path-to-template>",params);
        
```
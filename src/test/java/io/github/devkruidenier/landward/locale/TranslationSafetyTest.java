package io.github.devkruidenier.landward.locale;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class TranslationSafetyTest {
    private static final Pattern TOKENS=Pattern.compile("\\{[\\w-]+}|/(?:claimadmin|claim|landward)(?: [a-z][a-z-]*| <[^>]+>)*|plugins/LandWard/updates/|landward\\.[\\w.-]+");
    @Test void everyCatalogPreservesMessageArgumentsAndExecutableCommands() throws Exception {
        var english=load("en_US");
        for(String locale:LocaleCatalog.LOCALES){
            var translated=load(locale);
            assertEquals(english.keySet(),translated.keySet(),locale);
            for(var entry:english.entrySet()){
                String actual=translated.get(entry.getKey());
                assertNotNull(actual,locale+":"+entry.getKey());
                assertFalse(actual.isBlank(),locale+":"+entry.getKey());
                // Existing catalogs may localize descriptive <name>/<player> arguments.
                // New catalogs keep the full documented syntax verbatim; runtime {tokens}
                // must always survive in every language.
                boolean newCatalog=LocaleCatalog.LOCALES.indexOf(locale)>=13;
                assertEquals(tokens(entry.getValue(),false),tokens(actual,false),locale+":"+entry.getKey());
                if(newCatalog) for(String token:tokens(entry.getValue(),true))
                    assertTrue(actual.contains(token),locale+":"+entry.getKey()+" must preserve "+token);
                assertFalse(actual.matches("(?s).*(ZXQ\\d+ZXQ|LWT\\d+).*"),"Translation marker: "+locale+":"+entry.getKey());
                assertFalse(actual.contains("\uFFFD"),"Invalid Unicode: "+locale+":"+entry.getKey());
            }
        }
    }
    private List<String> tokens(String text,boolean commands){var values=new ArrayList<String>();(commands?TOKENS:Pattern.compile("\\{[\\w-]+}")).matcher(text).results().forEach(m->values.add(m.group())); Collections.sort(values);return values;}
    @SuppressWarnings("unchecked") private Map<String,String> load(String locale) throws Exception {
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);
        try(var input=getClass().getResourceAsStream("/lang/"+locale+".yml")){
            assertNotNull(input,"Missing "+locale);
            Map<String,Object> root=new Yaml(new SafeConstructor(options)).load(new InputStreamReader(input,StandardCharsets.UTF_8));
            var values=new TreeMap<String,String>();
            for(var section:root.entrySet()) for(var entry:((Map<String,Object>)section.getValue()).entrySet()) {
                assertInstanceOf(String.class,entry.getValue()); values.put(section.getKey()+"."+entry.getKey(),(String)entry.getValue());
            }
            return values;
        }
    }
}

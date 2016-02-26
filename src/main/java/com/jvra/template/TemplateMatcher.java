package com.jvra.template;

import org.apache.commons.lang3.text.StrSubstitutor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Jansel Valentin on 7/12/2014.
 *
 * This matcher is auto purged by a daemon, on LRU templates after a period of time.
 */
public final class TemplateMatcher {

    private static final String SENTINEL = "sentinel";

    private static TemplateMatcher single;

    private LinkedHashMap<String, String> cachedTemplates;

    private boolean autoPurge;
    private long intervalToPurge;
    private TimeUnit unit = TimeUnit.MINUTES;

    private TemplateMatcher() {
        cachedTemplates = new LinkedHashMap<>();
    }

    private TemplateMatcher(boolean autoPurge) {
        this(autoPurge, 60, TimeUnit.MINUTES);
    }

    private TemplateMatcher(boolean autoPurge, long intervalToPurge, TimeUnit unit) {
        cachedTemplates = new LinkedHashMap<>(10, .75f, autoPurge);

        this.intervalToPurge = intervalToPurge;
        this.autoPurge = autoPurge;
        this.unit = unit;
        startPurgeWorker();
    }

    public static TemplateMatcher single() {
        return null != single ? single : (single = new TemplateMatcher());
    }

    public static TemplateMatcher single(boolean autoPurge) {
        return null != single ? single : (single = new TemplateMatcher(autoPurge));
    }


    public static TemplateMatcher single(boolean autoPurge, long intervalToPurge, TimeUnit unit) {
        return null != single ? single : (single = new TemplateMatcher(autoPurge, intervalToPurge, unit));
    }

    public String template(String template, Map<String,String> params) {
        if (cachedTemplates.containsKey(template)) {
            return templateForParams(cachedTemplates.get(template), params);
        }

        String loadedTemplate = loadTemplate(template);
        if (null == loadedTemplate)
            return loadedTemplate;

        cachedTemplates.put(template, loadedTemplate);
        return templateForParams(loadedTemplate, params);
    }


    private String loadTemplate(String template) {
        Path path = Paths.get(template);
        if (!Files.exists(path))
            return null;

        StringBuilder builder = new StringBuilder();

        ByteBuffer buffer = ByteBuffer.allocate(8192);
        try (ReadableByteChannel channel = Channels.newChannel(new FileInputStream(path.toFile()))) {
            int i;
            for (; ; ) {
                i = channel.read(buffer);
                if (0 >= i)
                    break;

                buffer.flip();
                builder.append(Charset.forName("UTF-8").decode(buffer));
                buffer.clear();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            buffer.clear();
        }
        return builder.toString();
    }


    private String templateForParams(String loadedTemplate, Map<String,String> params) {
        return new StrSubstitutor(params).replace(loadedTemplate);
    }


    private void startPurgeWorker() {
        if (!autoPurge)
            return;
        if (0 >= intervalToPurge)
            intervalToPurge = 60;

        cachedTemplates.put(SENTINEL, SENTINEL);

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<String, String>> it;
                synchronized (cachedTemplates) {
                    it = cachedTemplates.entrySet().iterator();
                }

                if (null == it || !it.hasNext())
                    return;
                for (; ; ) {
                    Map.Entry<String, String> entry = it.next();
                    String template = entry.getKey();

                    if (SENTINEL.equalsIgnoreCase(template))
                        break;
                    it.remove();

                    if (!it.hasNext())
                        return;
                }

                cachedTemplates.get(SENTINEL);
            }
        }, intervalToPurge, intervalToPurge, unit);
    }
}

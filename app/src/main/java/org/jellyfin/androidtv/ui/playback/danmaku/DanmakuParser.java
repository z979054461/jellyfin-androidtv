package org.jellyfin.androidtv.ui.playback.danmaku;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

/** Parses only UTF-8 Bilibili XML; DTDs and external entities are never accepted. */
public final class DanmakuParser {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_COMMENTS = 20000;
    public static final int MAX_TEXT_LENGTH = 300;

    private DanmakuParser() { }

    public static List<DanmakuComment> parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Danmaku response exceeds size limit");
        final String xml;
        try {
            xml = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Danmaku must be UTF-8", e);
        }
        // Decode before examining declarations: alternative byte encodings cannot bypass this check.
        // XML requires these keywords literally (case-sensitive), including in an internal subset.
        if (xml.indexOf('\0') >= 0 || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw new IOException("DTD and entity declarations are not allowed");
        }
        List<DanmakuComment> result = new ArrayList<>();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            DefaultHandler handler = new DefaultHandler() {
                private String params;
                private StringBuilder text;
                private int depth;
                private int entries;

                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                    throw new SAXException("External entities are not allowed");
                }

                @Override
                public void startElement(String uri, String local, String name, Attributes attributes) throws SAXException {
                    if (++depth > 16) throw new SAXException("Excessive XML nesting");
                    if ("d".equals(name)) {
                        if (++entries > MAX_COMMENTS) throw new SAXException("Too many danmaku comments");
                        params = attributes.getValue("p");
                        text = new StringBuilder();
                    }
                }

                @Override
                public void characters(char[] chars, int start, int length) {
                    if (text != null && text.length() < MAX_TEXT_LENGTH) {
                        text.append(chars, start, Math.min(length, MAX_TEXT_LENGTH - text.length()));
                    }
                }

                @Override
                public void endElement(String uri, String local, String name) {
                    if ("d".equals(name) && text != null) {
                        addComment(result, params, text.toString());
                        params = null;
                        text = null;
                    }
                    depth--;
                }
            };
            String source = xml.startsWith("\uFEFF") ? xml.substring(1) : xml;
            factory.newSAXParser().parse(new InputSource(new StringReader(source)), handler);
        } catch (Exception e) {
            throw new IOException("Invalid danmaku XML", e);
        }
        Collections.sort(result, Comparator.comparingLong(comment -> comment.timeMs));
        return Collections.unmodifiableList(result);
    }

    private static void addComment(List<DanmakuComment> result, String params, String text) {
        if (params == null) return;
        String[] fields = params.split(",", 5);
        if (fields.length < 4) return;
        try {
            double seconds = Double.parseDouble(fields[0]);
            int mode = Integer.parseInt(fields[1]);
            long rgb = Long.parseLong(fields[3]);
            if ((Double.isNaN(seconds) || Double.isInfinite(seconds)) || seconds < 0 || seconds > 604800
                    || (mode != 1 && mode != 4 && mode != 5) || rgb < 0 || rgb > 0xffffff) return;
            text = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
            // Avoid leaving half of a surrogate pair after length limiting.
            if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
                text = text.substring(0, text.length() - 1);
            }
            if (!text.isEmpty()) result.add(new DanmakuComment((long) (seconds * 1000), mode, (int) rgb | 0xff000000, text));
        } catch (NumberFormatException ignored) {
            // A malformed entry must not suppress otherwise usable comments.
        }
    }
}

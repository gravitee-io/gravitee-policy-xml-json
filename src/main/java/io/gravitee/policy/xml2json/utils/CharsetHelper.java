/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.xml2json.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CharsetHelper {

    private static final Charset UTF_8_CHARSET = StandardCharsets.UTF_8;
    private static final String CHARSET_TAG = "charset=";
    private static final Pattern CHARSET_PATTERN = Pattern.compile(Pattern.quote(CHARSET_TAG), Pattern.CASE_INSENSITIVE);

    /**
     * Extract the charset from media-type as per https://tools.ietf.org/html/rfc7231#section-3.1.1.1
     *
     * @param mediaType
     * @return
     */
    public static Charset extractFromContentType(String mediaType) {
        if (mediaType == null || !CHARSET_PATTERN.matcher(mediaType).find()) {
            return UTF_8_CHARSET;
        }

        String charsetName = mediaType.substring(mediaType.lastIndexOf('=') + 1);
        charsetName = charsetName.replace("\"", "");

        return Charset.isSupported(charsetName) ? Charset.forName(charsetName) : Charset.defaultCharset();
    }
}

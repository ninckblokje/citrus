/*
 * Copyright 2006-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.http.integration;

import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.testng.TestNGCitrusSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testng.annotations.Test;

import static com.consol.citrus.http.actions.HttpActionBuilder.http;

/**
 * @author Christoph Deppisch
 */
@Test
public class HttpMultipartFileUploadJavaIT extends TestNGCitrusSupport {

    @CitrusTest
    public void httpMultipartFileUpload() {
        MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
        files.add("file", new ClassPathResource("com/consol/citrus/ws/soapAttachment.txt"));

        given(http().client("echoHttpClient")
            .send()
            .post()
            .fork(true)
            .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
            .payload(files));

        when(http().server("echoHttpServer")
            .receive()
            .post()
            .contentType("@startsWith(multipart/form-data)@")
            .messageType(MessageType.PLAINTEXT)
            .payload("@contains(This is an attachment!)@"));

       then(http().server("echoHttpServer")
           .send()
           .response(HttpStatus.OK));

       then(http().client("echoHttpClient")
            .receive()
            .response(HttpStatus.OK));
    }
}
